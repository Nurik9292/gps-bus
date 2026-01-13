package biz.ugur.busroutebackend.interfaces.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class WebSocketBufferMetricsTracker {

    private static final String METRICS_KEY_PREFIX = "metrics:websocket:buffer";
    private static final Duration METRICS_TTL = Duration.ofDays(7);
    private static final int OVERFLOW_LOG_THRESHOLD = 100;

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    private final AtomicLong totalEmitted = new AtomicLong(0);
    private final AtomicLong totalDropped = new AtomicLong(0);
    private final AtomicLong droppedSinceLastLog = new AtomicLong(0);
    private final AtomicLong lastLoggedDropCount = new AtomicLong(0);

    public WebSocketBufferMetricsTracker(ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void recordEmitted() {
        totalEmitted.incrementAndGet();
    }

    public void recordDropped(String vehicleId) {
        long dropped = totalDropped.incrementAndGet();
        long sinceLast = droppedSinceLastLog.incrementAndGet();

        if (sinceLast == 1) {
            log.warn("WEBSOCKET_BUFFER_OVERFLOW: First message dropped (vehicleId={}). " +
                     "Buffer is full - clients may miss position updates.", vehicleId);
        } else if (sinceLast % OVERFLOW_LOG_THRESHOLD == 0) {
            log.error("WEBSOCKET_BUFFER_OVERFLOW: {} messages dropped since last report (total: {}). " +
                      "Consider increasing buffer size or reducing message rate.",
                    sinceLast, dropped);
            incrementRedisCounter("dropped", sinceLast).subscribe();
            droppedSinceLastLog.set(0);
        }
    }

    public void recordDroppedBatch(int count) {
        if (count <= 0) return;

        long dropped = totalDropped.addAndGet(count);
        long sinceLast = droppedSinceLastLog.addAndGet(count);

        log.warn("WEBSOCKET_BUFFER_OVERFLOW: {} messages dropped in batch (total: {})",
                count, dropped);

        if (sinceLast >= OVERFLOW_LOG_THRESHOLD) {
            incrementRedisCounter("dropped", sinceLast).subscribe();
            droppedSinceLastLog.set(0);
        }
    }

    public BufferHealthStats getStats() {
        long emitted = totalEmitted.get();
        long dropped = totalDropped.get();
        return new BufferHealthStats(emitted, dropped);
    }

    public boolean isHealthy() {
        return getStats().dropRate() < 0.01;
    }

    public void resetCounters() {
        totalEmitted.set(0);
        totalDropped.set(0);
        droppedSinceLastLog.set(0);
        lastLoggedDropCount.set(0);
    }

    @Scheduled(fixedRate = 60000) // Every minute
    public void logHealthStatus() {
        BufferHealthStats stats = getStats();

        if (stats.totalDropped() > lastLoggedDropCount.get()) {
            long newDrops = stats.totalDropped() - lastLoggedDropCount.get();
            log.warn("WEBSOCKET_BUFFER_HEALTH: {} new drops in last minute (total: {}, rate: {:.2f}%)",
                    newDrops, stats.totalDropped(), stats.dropRate() * 100);
            lastLoggedDropCount.set(stats.totalDropped());

            incrementRedisCounter("dropped_minute", newDrops).subscribe();
        }

        if (stats.totalEmitted() > 0 && stats.totalEmitted() % 10000 == 0) {
            log.info("WEBSOCKET_BUFFER_STATS: emitted={}, dropped={}, rate={:.4f}%",
                    stats.totalEmitted(), stats.totalDropped(), stats.dropRate() * 100);
        }
    }

    private Mono<Long> incrementRedisCounter(String metric, long amount) {
        String key = METRICS_KEY_PREFIX + ":" + metric + ":" + LocalDate.now();

        return redisTemplate.opsForValue()
                .increment(key, amount)
                .flatMap(value -> redisTemplate.expire(key, METRICS_TTL).thenReturn(value))
                .onErrorResume(error -> {
                    log.trace("Failed to increment Redis counter {}: {}", key, error.getMessage());
                    return Mono.just(0L);
                });
    }

    public record BufferHealthStats(
            long totalEmitted,
            long totalDropped
    ) {
        public double dropRate() {
            long total = totalEmitted + totalDropped;
            if (total == 0) return 0.0;
            return (double) totalDropped / total;
        }


    }
}
