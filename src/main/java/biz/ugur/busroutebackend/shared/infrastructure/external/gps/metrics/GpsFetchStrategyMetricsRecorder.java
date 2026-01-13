package biz.ugur.busroutebackend.shared.infrastructure.external.gps.metrics;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class GpsFetchStrategyMetricsRecorder {

    private static final String KEY_PREFIX = "metrics:gps:fetch";
    private static final Duration METRICS_TTL = Duration.ofDays(7);

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    private final AtomicLong selectiveFetchCount = new AtomicLong(0);
    private final AtomicLong bulkFetchCount = new AtomicLong(0);
    private final AtomicLong cachedFetchCount = new AtomicLong(0);
    private final AtomicLong selectiveFetchDevices = new AtomicLong(0);
    private final AtomicLong bulkFetchDevices = new AtomicLong(0);

    public GpsFetchStrategyMetricsRecorder(ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void recordSelectiveFetch(int deviceCount, String provider) {
        selectiveFetchCount.incrementAndGet();
        selectiveFetchDevices.addAndGet(deviceCount);

        log.debug("[{}] Selective fetch strategy used for {} devices", provider, deviceCount);

        incrementRedisCounter("selective_fetch_count", 1).subscribe();
        incrementRedisCounter("selective_fetch_devices", deviceCount).subscribe();
    }

    public void recordBulkFetch(int requestedDevices, String provider) {
        bulkFetchCount.incrementAndGet();
        bulkFetchDevices.addAndGet(requestedDevices);

        log.debug("[{}] Bulk fetch strategy used for {} requested devices", provider, requestedDevices);

        incrementRedisCounter("bulk_fetch_count", 1).subscribe();
        incrementRedisCounter("bulk_fetch_devices", requestedDevices).subscribe();
    }

    public void recordCacheHit(int deviceCount, String provider) {
        cachedFetchCount.incrementAndGet();

        log.debug("[{}] Cache hit for {} devices", provider, deviceCount);

        incrementRedisCounter("cache_hit_count", 1).subscribe();
    }

    public GpsFetchStats getStats() {
        return new GpsFetchStats(
                selectiveFetchCount.get(),
                bulkFetchCount.get(),
                cachedFetchCount.get(),
                selectiveFetchDevices.get(),
                bulkFetchDevices.get()
        );
    }

    public void resetCounters() {
        selectiveFetchCount.set(0);
        bulkFetchCount.set(0);
        cachedFetchCount.set(0);
        selectiveFetchDevices.set(0);
        bulkFetchDevices.set(0);
    }

    private Mono<Long> incrementRedisCounter(String metric, long delta) {
        String key = KEY_PREFIX + ":" + metric + ":" + LocalDate.now();

        return redisTemplate.opsForValue()
                .increment(key, delta)
                .flatMap(value -> redisTemplate.expire(key, METRICS_TTL).thenReturn(value))
                .onErrorResume(error -> {
                    log.trace("Failed to increment Redis counter {}: {}", key, error.getMessage());
                    return Mono.just(0L);
                });
    }

    public record GpsFetchStats(
            long selectiveFetchCount,
            long bulkFetchCount,
            long cacheHitCount,
            long selectiveFetchDevices,
            long bulkFetchDevices
    ) {
        public long totalFetches() {
            return selectiveFetchCount + bulkFetchCount + cacheHitCount;
        }

        public double selectiveRatio() {
            long apiCalls = selectiveFetchCount + bulkFetchCount;
            if (apiCalls == 0) return 0.0;
            return (double) selectiveFetchCount / apiCalls * 100;
        }

        public double cacheHitRatio() {
            long total = totalFetches();
            if (total == 0) return 0.0;
            return (double) cacheHitCount / total * 100;
        }

        public double avgDevicesPerSelectiveFetch() {
            if (selectiveFetchCount == 0) return 0.0;
            return (double) selectiveFetchDevices / selectiveFetchCount;
        }

        public double avgDevicesPerBulkFetch() {
            if (bulkFetchCount == 0) return 0.0;
            return (double) bulkFetchDevices / bulkFetchCount;
        }
    }
}
