package biz.ugur.busroutebackend.transport.infrastructure.metrics;

import biz.ugur.busroutebackend.transport.domain.valueobject.GpsValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class GpsValidationMetricsRecorder {

    private static final String KEY_PREFIX = "metrics:gps:validation";
    private static final Duration METRICS_TTL = Duration.ofDays(7);
    private static final int LOG_INTERVAL_OUT_OF_BOUNDS = 100;

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    private final Map<GpsValidationResult, AtomicLong> counters;
    private final AtomicLong totalValidated = new AtomicLong(0);

    public GpsValidationMetricsRecorder(ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.counters = new EnumMap<>(GpsValidationResult.class);
        for (GpsValidationResult result : GpsValidationResult.values()) {
            counters.put(result, new AtomicLong(0));
        }
    }

    public void record(GpsValidationResult result, String deviceId) {
        totalValidated.incrementAndGet();
        long count = counters.get(result).incrementAndGet();

        if (result == GpsValidationResult.OUT_OF_BOUNDS) {
            if (count == 1) {
                log.warn("GPS_OUT_OF_BOUNDS: First out-of-bounds position detected for device {}. " +
                         "Check for GPS glitch, spoofing, or device malfunction.", deviceId);
            } else if (count % LOG_INTERVAL_OUT_OF_BOUNDS == 0) {
                log.warn("GPS_OUT_OF_BOUNDS: {} total out-of-bounds positions detected today. " +
                         "Latest device: {}", count, deviceId);
            }
        }

        incrementRedisCounter(result).subscribe();
    }

    public void recordBatch(Map<GpsValidationResult, Long> resultCounts) {
        resultCounts.forEach((result, count) -> {
            if (count > 0) {
                totalValidated.addAndGet(count);
                counters.get(result).addAndGet(count);
                incrementRedisCounterBy(result, count).subscribe();
            }
        });

        long outOfBounds = resultCounts.getOrDefault(GpsValidationResult.OUT_OF_BOUNDS, 0L);
        if (outOfBounds > 0) {
            log.warn("GPS_OUT_OF_BOUNDS: {} positions rejected as out-of-bounds in this batch",
                    outOfBounds);
        }
    }

    public GpsValidationStats getStats() {
        Map<GpsValidationResult, Long> counts = new EnumMap<>(GpsValidationResult.class);
        counters.forEach((result, counter) -> counts.put(result, counter.get()));

        return new GpsValidationStats(
                totalValidated.get(),
                counts.get(GpsValidationResult.VALID),
                counts.get(GpsValidationResult.OUT_OF_BOUNDS),
                counts.get(GpsValidationResult.NULL_COORDINATES),
                counts.get(GpsValidationResult.INVALID_DEVICE_ID),
                counts.get(GpsValidationResult.NULL_POSITION)
        );
    }

    public void resetCounters() {
        totalValidated.set(0);
        counters.values().forEach(counter -> counter.set(0));
    }

    private Mono<Long> incrementRedisCounter(GpsValidationResult result) {
        return incrementRedisCounterBy(result, 1);
    }

    private Mono<Long> incrementRedisCounterBy(GpsValidationResult result, long delta) {
        String key = buildKey(result);

        return redisTemplate.opsForValue()
                .increment(key, delta)
                .flatMap(value -> redisTemplate.expire(key, METRICS_TTL).thenReturn(value))
                .onErrorResume(error -> {
                    log.trace("Failed to increment Redis counter {}: {}", key, error.getMessage());
                    return Mono.just(0L);
                });
    }

    private String buildKey(GpsValidationResult result) {
        return KEY_PREFIX + ":" + result.name().toLowerCase() + ":" + LocalDate.now();
    }

    public record GpsValidationStats(
            long totalValidated,
            long valid,
            long outOfBounds,
            long nullCoordinates,
            long invalidDeviceId,
            long nullPosition
    ) {
        public long totalInvalid() {
            return outOfBounds + nullCoordinates + invalidDeviceId + nullPosition;
        }

        public double validRate() {
            if (totalValidated == 0) return 100.0;
            return (double) valid / totalValidated * 100;
        }

        public double outOfBoundsRate() {
            if (totalValidated == 0) return 0.0;
            return (double) outOfBounds / totalValidated * 100;
        }

        public boolean hasOutOfBoundsIssues() {
            return outOfBoundsRate() > 1.0;
        }
    }
}
