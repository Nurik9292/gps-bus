package biz.ugur.busroutebackend.shared.infrastructure.persistence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class OptimisticLockMetricsRecorder {

    private static final String KEY_PREFIX = "metrics:optimistic_lock";
    private static final Duration METRICS_TTL = Duration.ofDays(7);

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    private final AtomicLong totalRetries = new AtomicLong(0);
    private final AtomicLong totalFailuresAfterRetries = new AtomicLong(0);

    public OptimisticLockMetricsRecorder(ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void recordRetry(String entityType, String entityId, int retryAttempt, int maxRetries) {
        totalRetries.incrementAndGet();

        log.warn("Optimistic lock conflict for {} id={}, retry attempt {}/{}",
                entityType, entityId, retryAttempt, maxRetries);

        incrementRedisCounter(entityType, "retries")
                .subscribe();
    }

    public void recordFailureAfterRetries(String entityType, String entityId, int maxRetries) {
        totalFailuresAfterRetries.incrementAndGet();

        log.error("OPTIMISTIC_LOCK_FAILURE: {} id={} failed after {} retries - DATA MAY BE STALE. " +
                  "This vehicle's position was not updated due to concurrent modification.",
                entityType, entityId, maxRetries);

        incrementRedisCounter(entityType, "failures")
                .subscribe();
    }

    public void recordSuccessAfterRetry(String entityType, String entityId, int retriesUsed) {
        log.info("Optimistic lock resolved for {} id={} after {} retries",
                entityType, entityId, retriesUsed);

        incrementRedisCounter(entityType, "recovered")
                .subscribe();
    }

    public OptimisticLockStats getStats() {
        return new OptimisticLockStats(
                totalRetries.get(),
                totalFailuresAfterRetries.get()
        );
    }

    public void resetCounters() {
        totalRetries.set(0);
        totalFailuresAfterRetries.set(0);
    }

    private Mono<Long> incrementRedisCounter(String entityType, String metric) {
        String key = buildKey(entityType, metric);

        return redisTemplate.opsForValue()
                .increment(key)
                .flatMap(value -> redisTemplate.expire(key, METRICS_TTL).thenReturn(value))
                .onErrorResume(error -> {
                    log.debug("Failed to increment Redis counter {}: {}", key, error.getMessage());
                    return Mono.just(0L);
                });
    }

    private String buildKey(String entityType, String metric) {
        return KEY_PREFIX + ":" + entityType + ":" + metric + ":" + LocalDate.now();
    }

    public record OptimisticLockStats(
            long totalRetries,
            long totalFailuresAfterRetries
    ) {
        public boolean hasFailures() {
            return totalFailuresAfterRetries > 0;
        }

        public double failureRate() {
            if (totalRetries == 0) return 0.0;
            return (double) totalFailuresAfterRetries / totalRetries;
        }
    }
}
