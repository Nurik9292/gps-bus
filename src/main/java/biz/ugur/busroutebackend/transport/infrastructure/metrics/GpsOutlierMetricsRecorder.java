package biz.ugur.busroutebackend.transport.infrastructure.metrics;

import biz.ugur.busroutebackend.transport.domain.valueobject.OutlierDetectionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.DoubleAccumulator;

@Component
@Slf4j
public class GpsOutlierMetricsRecorder {

    private static final String KEY_PREFIX = "metrics:gps:outlier";
    private static final Duration METRICS_TTL = Duration.ofDays(7);
    private static final int LOG_INTERVAL_OUTLIERS = 50;

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    private final Map<OutlierDetectionResult.OutlierType, AtomicLong> counters;
    private final AtomicLong totalDetections = new AtomicLong(0);
    private final DoubleAccumulator maxImpliedSpeedSeen;
    private final DoubleAccumulator avgImpliedSpeedSum;
    private final AtomicLong avgImpliedSpeedCount = new AtomicLong(0);

    public GpsOutlierMetricsRecorder(ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.counters = new EnumMap<>(OutlierDetectionResult.OutlierType.class);
        for (OutlierDetectionResult.OutlierType type : OutlierDetectionResult.OutlierType.values()) {
            counters.put(type, new AtomicLong(0));
        }
        this.maxImpliedSpeedSeen = new DoubleAccumulator(Double::max, 0.0);
        this.avgImpliedSpeedSum = new DoubleAccumulator(Double::sum, 0.0);
    }

    public void record(OutlierDetectionResult result) {
        totalDetections.incrementAndGet();
        long count = counters.get(result.type()).incrementAndGet();

        if (result.wasEvaluated()) {
            avgImpliedSpeedSum.accumulate(result.impliedSpeedKmh());
            avgImpliedSpeedCount.incrementAndGet();

            if (result.impliedSpeedKmh() > maxImpliedSpeedSeen.get()) {
                maxImpliedSpeedSeen.accumulate(result.impliedSpeedKmh());
            }
        }

        if (result.isOutlier()) {
            if (count == 1) {
                log.warn("GPS_OUTLIER: First outlier detected for device {}. " +
                                "Implied speed: {:.1f} km/h (max allowed: {:.1f} km/h). " +
                                "Distance: {:.0f}m in {}s. Possible GPS glitch or device issue.",
                        result.deviceId(), result.impliedSpeedKmh(), result.maxAllowedSpeedKmh(),
                        result.distanceMeters(), result.timeDifferenceSeconds());
            } else if (count % LOG_INTERVAL_OUTLIERS == 0) {
                log.warn("GPS_OUTLIER: {} total outliers detected today. Latest device: {} " +
                                "with implied speed {:.1f} km/h",
                        count, result.deviceId(), result.impliedSpeedKmh());
            }
        }

        incrementRedisCounter(result.type()).subscribe();

        if (result.isOutlier()) {
            recordOutlierDetails(result).subscribe();
        }
    }

    public void recordBatch(Iterable<OutlierDetectionResult> results) {
        Map<OutlierDetectionResult.OutlierType, Long> batchCounts = new EnumMap<>(OutlierDetectionResult.OutlierType.class);
        int outlierCount = 0;

        for (OutlierDetectionResult result : results) {
            totalDetections.incrementAndGet();
            counters.get(result.type()).incrementAndGet();
            batchCounts.merge(result.type(), 1L, Long::sum);

            if (result.wasEvaluated()) {
                avgImpliedSpeedSum.accumulate(result.impliedSpeedKmh());
                avgImpliedSpeedCount.incrementAndGet();
                maxImpliedSpeedSeen.accumulate(result.impliedSpeedKmh());
            }

            if (result.isOutlier()) {
                outlierCount++;
            }
        }

        batchCounts.forEach((type, count) -> incrementRedisCounterBy(type, count).subscribe());

        if (outlierCount > 0) {
            log.warn("GPS_OUTLIER: {} outliers detected in this batch", outlierCount);
        }
    }

    public GpsOutlierStats getStats() {
        long total = totalDetections.get();
        long valid = counters.get(OutlierDetectionResult.OutlierType.VALID).get();
        long outliers = counters.get(OutlierDetectionResult.OutlierType.SPEED_EXCEEDED).get();
        long noHistory = counters.get(OutlierDetectionResult.OutlierType.NO_HISTORY).get();
        long timeGapTooLarge = counters.get(OutlierDetectionResult.OutlierType.TIME_GAP_TOO_LARGE).get();
        long timeGapTooSmall = counters.get(OutlierDetectionResult.OutlierType.TIME_GAP_TOO_SMALL).get();
        long distanceTooSmall = counters.get(OutlierDetectionResult.OutlierType.DISTANCE_TOO_SMALL).get();
        long disabled = counters.get(OutlierDetectionResult.OutlierType.DETECTION_DISABLED).get();

        double avgSpeed = avgImpliedSpeedCount.get() > 0
                ? avgImpliedSpeedSum.get() / avgImpliedSpeedCount.get()
                : 0.0;

        return new GpsOutlierStats(
                total,
                valid,
                outliers,
                noHistory,
                timeGapTooLarge,
                timeGapTooSmall,
                distanceTooSmall,
                disabled,
                maxImpliedSpeedSeen.get(),
                avgSpeed
        );
    }

    public void resetCounters() {
        totalDetections.set(0);
        counters.values().forEach(counter -> counter.set(0));
        maxImpliedSpeedSeen.reset();
        avgImpliedSpeedSum.reset();
        avgImpliedSpeedCount.set(0);
    }

    private Mono<Long> incrementRedisCounter(OutlierDetectionResult.OutlierType type) {
        return incrementRedisCounterBy(type, 1);
    }

    private Mono<Long> incrementRedisCounterBy(OutlierDetectionResult.OutlierType type, long delta) {
        String key = buildKey(type);

        return redisTemplate.opsForValue()
                .increment(key, delta)
                .flatMap(value -> redisTemplate.expire(key, METRICS_TTL).thenReturn(value))
                .onErrorResume(error -> {
                    log.trace("Failed to increment Redis counter {}: {}", key, error.getMessage());
                    return Mono.just(0L);
                });
    }

    private Mono<Void> recordOutlierDetails(OutlierDetectionResult result) {
        String key = KEY_PREFIX + ":details:" + LocalDate.now();

        String detail = String.format("%s|%.1f|%.0f|%d",
                result.deviceId(),
                result.impliedSpeedKmh(),
                result.distanceMeters(),
                result.timeDifferenceSeconds());

        return redisTemplate.opsForList()
                .leftPush(key, detail)
                .flatMap(size -> {
                    if (size == 1) {
                        return redisTemplate.expire(key, METRICS_TTL);
                    }
                    return Mono.just(true);
                })
                .then()
                .onErrorResume(error -> {
                    log.trace("Failed to record outlier details: {}", error.getMessage());
                    return Mono.empty();
                });
    }

    private String buildKey(OutlierDetectionResult.OutlierType type) {
        return KEY_PREFIX + ":" + type.name().toLowerCase() + ":" + LocalDate.now();
    }

    public record GpsOutlierStats(
            long totalDetections,
            long valid,
            long outliers,
            long noHistory,
            long timeGapTooLarge,
            long timeGapTooSmall,
            long distanceTooSmall,
            long disabled,
            double maxImpliedSpeedKmh,
            double avgImpliedSpeedKmh
    ) {
        public long totalEvaluated() {
            return valid + outliers;
        }

        public long totalSkipped() {
            return noHistory + timeGapTooLarge + timeGapTooSmall + distanceTooSmall + disabled;
        }

        public double outlierRate() {
            long evaluated = totalEvaluated();
            if (evaluated == 0) return 0.0;
            return (double) outliers / evaluated * 100;
        }

        public double evaluationRate() {
            if (totalDetections == 0) return 0.0;
            return (double) totalEvaluated() / totalDetections * 100;
        }

        public boolean hasOutlierIssues() {
            return outlierRate() > 5.0;
        }
    }
}
