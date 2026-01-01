package biz.ugur.busroutebackend.transport.scheduler.service;

import biz.ugur.busroutebackend.shared.infrastructure.cache.RedisKeyRegistry;
import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionUpdateResult;
import biz.ugur.busroutebackend.transport.scheduler.dto.GpsUpdateStats;
import biz.ugur.busroutebackend.transport.scheduler.dto.HealthStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;


@Service
@Slf4j
public class GpsUpdateStatisticsService {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    public GpsUpdateStatisticsService(ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<Void> saveUpdateStats(VehiclePositionUpdateResult result, Duration duration) {
        GpsUpdateStats stats = new GpsUpdateStats(
                result.updatedCount(),
                result.createdCount(),
                result.failedCount(),
                result.invalidCount(),
                result.conflictCount(),
                duration.toMillis(),
                Instant.now(),
                true
        );

        return saveStats(stats);
    }

    public Mono<Void> saveUpdateError(Throwable error, Duration duration) {
        GpsUpdateStats stats = new GpsUpdateStats(
                0, 0, 0, 0, 0,
                duration.toMillis(),
                Instant.now(),
                false,
                error.getMessage()
        );

        return saveStats(stats);
    }

    private Mono<Void> saveStats(GpsUpdateStats stats) {
        String key = RedisKeyRegistry.Gps.updateStats(Instant.now());

        return redisTemplate.opsForValue()
                .set(key, stats, RedisKeyRegistry.Gps.STATS_TTL)
                .then()
                .onErrorResume(error -> {
                    log.warn("Failed to save GPS stats to Redis: {}", error.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Void> saveHealthStatus(boolean healthy) {
        String key = RedisKeyRegistry.Gps.health();
        HealthStatus status = new HealthStatus(healthy, Instant.now());

        return redisTemplate.opsForValue()
                .set(key, status, RedisKeyRegistry.Gps.HEALTH_TTL)
                .then()
                .onErrorResume(error -> {
                    log.warn("Failed to save health status to Redis: {}", error.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Long> cleanupOldStats() {
        String pattern = RedisKeyRegistry.Gps.statsPattern();
        int cleanupDays = RedisKeyRegistry.Gps.STATS_CLEANUP_DAYS;

        return redisTemplate.keys(pattern)
                .filter(key -> isOlderThanDays(key, cleanupDays))
                .flatMap(redisTemplate::delete)
                .collectList()
                .map(deleted -> (long) deleted.size())
                .doOnSuccess(count -> {
                    if (count > 0) {
                        log.info("Cleaned up {} old GPS stats entries", count);
                    }
                })
                .onErrorResume(error -> {
                    log.warn("Failed to cleanup old GPS stats: {}", error.getMessage());
                    return Mono.just(0L);
                });
    }

    private boolean isOlderThanDays(String key, int days) {
        try {
            String[] parts = key.split(":");
            long timestamp = Long.parseLong(parts[parts.length - 1]);
            long cutoff = Instant.now().minus(Duration.ofDays(days)).getEpochSecond();
            return timestamp < cutoff;
        } catch (Exception e) {
            return false;
        }
    }
}
