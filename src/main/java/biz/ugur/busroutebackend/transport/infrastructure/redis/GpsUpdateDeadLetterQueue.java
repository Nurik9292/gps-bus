package biz.ugur.busroutebackend.transport.infrastructure.redis;

import biz.ugur.busroutebackend.transport.domain.valueobject.FailedGpsUpdate;
import biz.ugur.busroutebackend.transport.infrastructure.config.GpsDeadLetterQueueProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class GpsUpdateDeadLetterQueue {

    private static final String PENDING_KEY_PREFIX = "dlq:gps:pending:";
    private static final String PERMANENT_KEY_PREFIX = "dlq:gps:permanent:";
    private static final String METRICS_KEY_PREFIX = "dlq:gps:metrics:";
    private static final String PENDING_INDEX_KEY = "dlq:gps:pending:index";

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final GpsDeadLetterQueueProperties properties;
    private final ObjectMapper objectMapper;

    private final AtomicLong enqueuedCount = new AtomicLong(0);
    private final AtomicLong retriedCount = new AtomicLong(0);
    private final AtomicLong permanentFailureCount = new AtomicLong(0);

    public GpsUpdateDeadLetterQueue(
            ReactiveRedisTemplate<String, Object> redisTemplate,
            GpsDeadLetterQueueProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        log.info("GpsUpdateDeadLetterQueue initialized: enabled={}, maxRetries={}, ttl={}",
                properties.isEnabled(), properties.getMaxRetries(), properties.getTtl());
    }

    public Mono<Void> enqueue(FailedGpsUpdate failedUpdate) {
        if (!properties.isEnabled()) {
            log.debug("DLQ disabled, skipping enqueue for device {}", failedUpdate.deviceId());
            return Mono.empty();
        }

        return Mono.fromCallable(() -> serialize(failedUpdate))
                .flatMap(json -> {
                    String key = PENDING_KEY_PREFIX + failedUpdate.id();
                    Duration ttl = properties.getTtl();

                    return redisTemplate.opsForValue()
                            .set(key, json, ttl)
                            .then(redisTemplate.opsForSet().add(PENDING_INDEX_KEY, failedUpdate.id()))
                            .then(redisTemplate.expire(PENDING_INDEX_KEY, ttl))
                            .then(incrementMetric("enqueued"))
                            .doOnSuccess(v -> {
                                enqueuedCount.incrementAndGet();
                                if (properties.isLogFailures()) {
                                    log.warn("GPS_DLQ: Enqueued failed update for device {} (type: {}, reason: {})",
                                            failedUpdate.deviceId(),
                                            failedUpdate.failureType(),
                                            failedUpdate.failureReason());
                                }
                            });
                })
                .onErrorResume(error -> {
                    log.error("GPS_DLQ: Failed to enqueue update for device {}: {}",
                            failedUpdate.deviceId(), error.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    public Flux<FailedGpsUpdate> getPendingForRetry() {
        if (!properties.isEnabled()) {
            return Flux.empty();
        }

        LocalDateTime now = LocalDateTime.now();

        return redisTemplate.opsForSet()
                .members(PENDING_INDEX_KEY)
                .take(properties.getRetryBatchSize())
                .flatMap(id -> getFailedUpdate(PENDING_KEY_PREFIX + id))
                .filter(update -> isReadyForRetry(update, now));
    }

    public Mono<Void> markRetried(FailedGpsUpdate failedUpdate) {
        if (!properties.isEnabled()) {
            return Mono.empty();
        }

        String key = PENDING_KEY_PREFIX + failedUpdate.id();

        return redisTemplate.delete(key)
                .then(redisTemplate.opsForSet().remove(PENDING_INDEX_KEY, failedUpdate.id()))
                .then(incrementMetric("retried_success"))
                .doOnSuccess(v -> {
                    retriedCount.incrementAndGet();
                    log.info("GPS_DLQ: Successfully retried update for device {} after {} attempts",
                            failedUpdate.deviceId(), failedUpdate.retryCount() + 1);
                })
                .then();
    }

    public Mono<Void> requeue(FailedGpsUpdate failedUpdate) {
        if (!properties.isEnabled()) {
            return Mono.empty();
        }

        if (!failedUpdate.canRetry(properties.getMaxRetries())) {
            return moveToPermanentFailure(failedUpdate);
        }

        Duration nextDelay = properties.calculateNextRetryDelay(failedUpdate.retryCount());
        LocalDateTime nextRetryAt = LocalDateTime.now().plus(nextDelay);
        FailedGpsUpdate updated = failedUpdate.withRetry(nextRetryAt);

        return Mono.fromCallable(() -> serialize(updated))
                .flatMap(json -> {
                    String key = PENDING_KEY_PREFIX + updated.id();

                    return redisTemplate.opsForValue()
                            .set(key, json, properties.getTtl())
                            .then(incrementMetric("requeued"))
                            .doOnSuccess(v ->
                                    log.debug("GPS_DLQ: Requeued device {} for retry #{} at {}",
                                            updated.deviceId(), updated.retryCount(), nextRetryAt)
                            );
                })
                .onErrorResume(error -> {
                    log.error("GPS_DLQ: Failed to requeue update for device {}: {}",
                            failedUpdate.deviceId(), error.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> moveToPermanentFailure(FailedGpsUpdate failedUpdate) {
        return Mono.fromCallable(() -> serialize(failedUpdate))
                .flatMap(json -> {
                    String pendingKey = PENDING_KEY_PREFIX + failedUpdate.id();
                    String permanentKey = PERMANENT_KEY_PREFIX + failedUpdate.id();

                    return redisTemplate.delete(pendingKey)
                            .then(redisTemplate.opsForSet().remove(PENDING_INDEX_KEY, failedUpdate.id()))
                            .then(redisTemplate.opsForValue().set(permanentKey, json, properties.getTtl()))
                            .then(incrementMetric("permanent_failure"))
                            .doOnSuccess(v -> {
                                permanentFailureCount.incrementAndGet();
                                log.error("GPS_DLQ: Moved to permanent failure after {} retries: device={}, type={}, reason={}",
                                        failedUpdate.retryCount(),
                                        failedUpdate.deviceId(),
                                        failedUpdate.failureType(),
                                        failedUpdate.failureReason());
                            });
                })
                .onErrorResume(error -> {
                    log.error("GPS_DLQ: Failed to move to permanent failure: {}", error.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    public Mono<DlqStats> getStats() {
        return redisTemplate.opsForSet()
                .size(PENDING_INDEX_KEY)
                .map(pendingCount -> new DlqStats(
                        pendingCount,
                        enqueuedCount.get(),
                        retriedCount.get(),
                        permanentFailureCount.get()
                ));
    }

    public void resetCounters() {
        enqueuedCount.set(0);
        retriedCount.set(0);
        permanentFailureCount.set(0);
    }

    private boolean isReadyForRetry(FailedGpsUpdate update, LocalDateTime now) {
        if (update.nextRetryAt() == null) {
            return true;
        }
        return !now.isBefore(update.nextRetryAt());
    }

    private Mono<FailedGpsUpdate> getFailedUpdate(String key) {
        return redisTemplate.opsForValue()
                .get(key)
                .flatMap(value -> Mono.fromCallable(() -> deserialize(value.toString())))
                .onErrorResume(error -> {
                    log.trace("GPS_DLQ: Failed to deserialize update from {}: {}", key, error.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Boolean> incrementMetric(String metric) {
        String key = METRICS_KEY_PREFIX + metric + ":" + java.time.LocalDate.now();
        return redisTemplate.opsForValue()
                .increment(key)
                .flatMap(v -> redisTemplate.expire(key, Duration.ofDays(7)))
                .onErrorReturn(false);
    }

    private String serialize(FailedGpsUpdate update) throws JsonProcessingException {
        return objectMapper.writeValueAsString(update);
    }

    private FailedGpsUpdate deserialize(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, FailedGpsUpdate.class);
    }

    public record DlqStats(
            long pendingCount,
            long totalEnqueued,
            long successfulRetries,
            long permanentFailures
    ) {
        public double retrySuccessRate() {
            long total = successfulRetries + permanentFailures;
            if (total == 0) return 100.0;
            return (double) successfulRetries / total * 100;
        }
    }
}
