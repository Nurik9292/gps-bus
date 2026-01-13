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
    private final AtomicReference<Instant> lastFailureTime = new AtomicReference<>();
    private final AtomicReference<Instant> lastSuccessTime = new AtomicReference<>();
    private final AtomicReference<String> lastErrorMessage = new AtomicReference<>();

    public RedisPubSubHealthTracker(ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void recordSuccess(String channel) {
        long previousConsecutiveFailures = consecutiveFailures.getAndSet(0);
        totalSuccesses.incrementAndGet();
        lastSuccessTime.set(Instant.now());

        if (!healthy.getAndSet(true)) {
            log.info("REDIS_PUBSUB_RECOVERED: Channel '{}' is healthy again after {} consecutive failures",
                    channel, previousConsecutiveFailures);
        }

        incrementRedisCounter("success").subscribe();
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
                lastFailureTime.get(),
                lastSuccessTime.get(),
                lastErrorMessage.get()
        );
    }

    public void resetCounters() {
        consecutiveFailures.set(0);
        totalFailures.set(0);
        totalSuccesses.set(0);
        healthy.set(true);
        lastFailureTime.set(null);
        lastSuccessTime.set(null);
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
            Instant lastFailureTime,
            Instant lastSuccessTime,
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
