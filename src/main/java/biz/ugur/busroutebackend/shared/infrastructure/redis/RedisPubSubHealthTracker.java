package biz.ugur.busroutebackend.shared.infrastructure.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
public class RedisPubSubHealthTracker {

    private static final String METRICS_KEY_PREFIX = "metrics:redis:pubsub";
    private static final Duration METRICS_TTL = Duration.ofDays(7);
    private static final int FAILURE_LOG_INTERVAL = 10;

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    private final AtomicBoolean healthy = new AtomicBoolean(true);
    private final AtomicLong consecutiveFailures = new AtomicLong(0);
    private final AtomicLong totalFailures = new AtomicLong(0);
    private final AtomicLong totalSuccesses = new AtomicLong(0);
    private final AtomicLong reconnectAttempts = new AtomicLong(0);
    private final AtomicLong totalDowntimeMs = new AtomicLong(0);
    private final AtomicReference<Instant> lastFailureTime = new AtomicReference<>();
    private final AtomicReference<Instant> lastSuccessTime = new AtomicReference<>();
    private final AtomicReference<Instant> lastDisconnectAt = new AtomicReference<>();
    private final AtomicReference<String> lastErrorMessage = new AtomicReference<>();

    public RedisPubSubHealthTracker(ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void recordSuccess(String channel) {
        long previousConsecutiveFailures = consecutiveFailures.getAndSet(0);
        totalSuccesses.incrementAndGet();
        Instant now = Instant.now();
        lastSuccessTime.set(now);

        if (!healthy.getAndSet(true)) {
            Instant disconnectedAt = lastDisconnectAt.getAndSet(null);
            if (disconnectedAt != null) {
                long downtimeMs = Duration.between(disconnectedAt, now).toMillis();
                totalDowntimeMs.addAndGet(downtimeMs);
                log.info("REDIS_PUBSUB_RECOVERED: Channel '{}' is healthy again after {} consecutive failures (downtime {}ms)",
                        channel, previousConsecutiveFailures, downtimeMs);
            } else {
                log.info("REDIS_PUBSUB_RECOVERED: Channel '{}' is healthy again after {} consecutive failures",
                        channel, previousConsecutiveFailures);
            }
        }

        incrementRedisCounter("success").subscribe();
    }

    public void recordReconnectAttempt(long retryCount, Throwable cause) {
        long attempts = reconnectAttempts.incrementAndGet();
        if (lastDisconnectAt.get() == null) {
            lastDisconnectAt.set(Instant.now());
        }
        log.warn("REDIS_PUBSUB_RECONNECT attempt {} (total={}) cause={}",
                retryCount + 1, attempts, cause != null ? cause.getMessage() : "unknown");
    }

    public void recordFailure(String channel, Throwable error) {
        long failures = consecutiveFailures.incrementAndGet();
        long total = totalFailures.incrementAndGet();
        lastFailureTime.set(Instant.now());
        lastErrorMessage.set(error.getMessage());
        healthy.set(false);

        if (failures == 1) {
            log.error("REDIS_PUBSUB_FAILURE: Failed to publish to channel '{}': {}. " +
                      "Multi-instance synchronization may be affected.",
                    channel, error.getMessage());
        } else if (failures % FAILURE_LOG_INTERVAL == 0) {
            log.error("REDIS_PUBSUB_FAILURE: Channel '{}' has {} consecutive failures (total: {}). " +
                      "Last error: {}. WebSocket clients on other instances are NOT receiving updates.",
                    channel, failures, total, error.getMessage());
        } else {
            log.warn("REDIS_PUBSUB_FAILURE: Channel '{}' publish failed ({} consecutive). Error: {}",
                    channel, failures, error.getMessage());
        }

        incrementRedisCounter("failure").subscribe();
    }

    public boolean isHealthy() {
        return healthy.get();
    }

    public Mono<Boolean> getHealthStatus() {
        return Mono.just(healthy.get());
    }

    public RedisPubSubHealthStats getStats() {
        return new RedisPubSubHealthStats(
                healthy.get(),
                consecutiveFailures.get(),
                totalFailures.get(),
                totalSuccesses.get(),
                reconnectAttempts.get(),
                totalDowntimeMs.get(),
                lastFailureTime.get(),
                lastSuccessTime.get(),
                lastDisconnectAt.get(),
                lastErrorMessage.get()
        );
    }

    public void resetCounters() {
        consecutiveFailures.set(0);
        totalFailures.set(0);
        totalSuccesses.set(0);
        reconnectAttempts.set(0);
        totalDowntimeMs.set(0);
        healthy.set(true);
        lastFailureTime.set(null);
        lastSuccessTime.set(null);
        lastDisconnectAt.set(null);
        lastErrorMessage.set(null);
    }

    private Mono<Long> incrementRedisCounter(String metric) {
        String key = METRICS_KEY_PREFIX + ":" + metric + ":" + LocalDate.now();

        return redisTemplate.opsForValue()
                .increment(key)
                .flatMap(value -> redisTemplate.expire(key, METRICS_TTL).thenReturn(value))
                .onErrorResume(error -> {
                    log.trace("Failed to increment Redis Pub/Sub metric {}: {}", key, error.getMessage());
                    return Mono.just(0L);
                });
    }

    public record RedisPubSubHealthStats(
            boolean healthy,
            long consecutiveFailures,
            long totalFailures,
            long totalSuccesses,
            long reconnectAttempts,
            long totalDowntimeMs,
            Instant lastFailureTime,
            Instant lastSuccessTime,
            Instant lastDisconnectAt,
            String lastErrorMessage
    ) {
        public double successRate() {
            long total = totalFailures + totalSuccesses;
            if (total == 0) return 1.0;
            return (double) totalSuccesses / total;
        }

        public boolean hasRecentFailures() {
            if (lastFailureTime == null) return false;
            return lastFailureTime.isAfter(Instant.now().minus(Duration.ofMinutes(5)));
        }
    }
}
