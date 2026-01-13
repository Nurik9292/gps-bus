package biz.ugur.busroutebackend.shared.infrastructure.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
@RequiredArgsConstructor
public class RedisTimeoutHandler {

    private static final String METRICS_KEY_PREFIX = "metrics:redis:timeout";
    private static final Duration METRICS_TTL = Duration.ofDays(7);

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final RedisOperationProperties properties;

    private final AtomicLong totalTimeouts = new AtomicLong(0);
    private final AtomicLong totalOperations = new AtomicLong(0);

    public <T> Mono<T> wrapWithTimeout(Mono<T> operation, String operationName, String context) {
        return wrapWithTimeout(operation, operationName, context, properties.getReadTimeout());
    }

    public <T> Mono<T> wrapWithTimeout(Mono<T> operation, String operationName,
                                        String context, Duration timeout) {
        totalOperations.incrementAndGet();

        return operation
                .timeout(timeout)
                .doOnError(TimeoutException.class, e -> handleTimeout(operationName, context))
                .onErrorResume(TimeoutException.class, e -> Mono.empty());
    }

    public <T> Mono<T> wrapWriteWithTimeout(Mono<T> operation, String operationName, String context) {
        return wrapWithTimeout(operation, operationName, context, properties.getWriteTimeout());
    }

    public <T> Flux<T> wrapFluxWithTimeout(Flux<T> operation, String operationName, String context) {
        return wrapFluxWithTimeout(operation, operationName, context, properties.getReadTimeout());
    }

    public <T> Flux<T> wrapFluxWithTimeout(Flux<T> operation, String operationName,
                                            String context, Duration timeout) {
        totalOperations.incrementAndGet();

        return operation
                .timeout(timeout)
                .doOnError(TimeoutException.class, e -> handleTimeout(operationName, context))
                .onErrorResume(TimeoutException.class, e -> Flux.empty());
    }

    public TimeoutStats getStats() {
        long timeouts = totalTimeouts.get();
        long operations = totalOperations.get();
        double rate = operations > 0 ? (double) timeouts / operations * 100 : 0.0;

        return new TimeoutStats(timeouts, operations, rate);
    }

    public void resetCounters() {
        totalTimeouts.set(0);
        totalOperations.set(0);
    }

    private void handleTimeout(String operationName, String context) {
        long timeouts = totalTimeouts.incrementAndGet();

        log.warn("REDIS_TIMEOUT: Operation '{}' timed out for {}. Total timeouts: {}",
                operationName, context, timeouts);

        incrementTimeoutMetric(operationName).subscribe();
    }

    private Mono<Long> incrementTimeoutMetric(String operationName) {
        String key = METRICS_KEY_PREFIX + ":" + operationName + ":" + LocalDate.now();

        return redisTemplate.opsForValue()
                .increment(key)
                .flatMap(value -> redisTemplate.expire(key, METRICS_TTL).thenReturn(value))
                .onErrorResume(error -> {
                    log.trace("Failed to record timeout metric: {}", error.getMessage());
                    return Mono.just(0L);
                });
    }

    public record TimeoutStats(
            long totalTimeouts,
            long totalOperations,
            double timeoutRatePercent
    ) {
        public boolean isHealthy() {
            return timeoutRatePercent < 5.0;
        }
    }
}
