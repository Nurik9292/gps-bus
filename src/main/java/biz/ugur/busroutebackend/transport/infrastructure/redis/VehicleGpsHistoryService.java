package biz.ugur.busroutebackend.transport.infrastructure.redis;

import biz.ugur.busroutebackend.shared.infrastructure.redis.RedisTimeoutHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class VehicleGpsHistoryService {

    private static final String KEY_PREFIX = "gps:history:";
    private static final String OPERATION_ADD_POINT = "gps_history_add";
    private static final String OPERATION_GET_HISTORY = "gps_history_get";
    private static final String OPERATION_GET_COUNT = "gps_history_count";
    private static final String OPERATION_CLEAR = "gps_history_clear";

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final RedisTimeoutHandler timeoutHandler;

    @Value("${business.gps-history.max-points:100}")
    private int maxPoints;

    @Value("${business.gps-history.ttl-minutes:30}")
    private int ttlMinutes;

    public VehicleGpsHistoryService(ReactiveRedisTemplate<String, Object> redisTemplate,
                                    ObjectMapper objectMapper,
                                    RedisTimeoutHandler timeoutHandler) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.timeoutHandler = timeoutHandler;
    }

    public Mono<Void> addPoint(String vehicleId, Double latitude, Double longitude,
                                Double speed, LocalDateTime timestamp) {
        if (vehicleId == null || latitude == null || longitude == null) {
            return Mono.empty();
        }

        String key = KEY_PREFIX + vehicleId;
        GpsPoint point = GpsPoint.of(latitude, longitude, speed, timestamp);

        try {
            String jsonPoint = objectMapper.writeValueAsString(point);

            Mono<Void> operation = redisTemplate.opsForList().leftPush(key, jsonPoint)
                    .then(redisTemplate.opsForList().trim(key, 0, maxPoints - 1))
                    .then(redisTemplate.expire(key, Duration.ofMinutes(ttlMinutes)))
                    .then();

            return timeoutHandler.wrapWriteWithTimeout(
                    operation,
                    OPERATION_ADD_POINT,
                    "vehicleId=" + vehicleId
            ).doOnError(e -> log.error("Failed to add GPS point for vehicle {}: {}",
                    vehicleId, e.getMessage()));

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize GPS point for vehicle {}: {}", vehicleId, e.getMessage());
            return Mono.empty();
        }
    }

    public Flux<GpsPoint> getHistory(String vehicleId, int limit) {
        if (vehicleId == null) {
            return Flux.empty();
        }

        String key = KEY_PREFIX + vehicleId;
        int effectiveLimit = limit > 0 ? Math.min(limit, maxPoints) : maxPoints;

        Flux<GpsPoint> operation = redisTemplate.opsForList()
                .range(key, 0, effectiveLimit - 1)
                .mapNotNull(this::deserializePoint);

        return timeoutHandler.wrapFluxWithTimeout(
                operation,
                OPERATION_GET_HISTORY,
                "vehicleId=" + vehicleId
        ).doOnError(e -> log.error("Failed to get GPS history for vehicle {}: {}",
                vehicleId, e.getMessage()));
    }

    public Flux<GpsPoint> getHistory(String vehicleId) {
        return getHistory(vehicleId, maxPoints);
    }

    public Mono<List<GpsPoint>> getHistoryList(String vehicleId, int limit) {
        return getHistory(vehicleId, limit).collectList();
    }

    public Mono<java.util.Map<String, List<GpsPoint>>> getHistoryBatch(List<String> vehicleIds, int limit) {
        if (vehicleIds == null || vehicleIds.isEmpty()) {
            return Mono.just(java.util.Collections.emptyMap());
        }
        return Flux.fromIterable(vehicleIds)
                .flatMap(vid -> getHistoryList(vid, limit)
                        .map(history -> java.util.Map.entry(vid, history))
                        .onErrorResume(err -> {
                            log.warn("[GPS_PIPELINE] history batch fetch failed for {}: {}", vid, err.getMessage());
                            return Mono.just(java.util.Map.entry(vid, java.util.List.<GpsPoint>of()));
                        }), 32)
                .collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey, java.util.Map.Entry::getValue));
    }

    public Mono<Long> getPointCount(String vehicleId) {
        if (vehicleId == null) {
            return Mono.just(0L);
        }

        String key = KEY_PREFIX + vehicleId;

        Mono<Long> operation = redisTemplate.opsForList().size(key)
                .defaultIfEmpty(0L);

        return timeoutHandler.wrapWithTimeout(
                operation,
                OPERATION_GET_COUNT,
                "vehicleId=" + vehicleId
        ).defaultIfEmpty(0L);
    }

    public Mono<Boolean> clearHistory(String vehicleId) {
        if (vehicleId == null) {
            return Mono.just(false);
        }

        String key = KEY_PREFIX + vehicleId;

        Mono<Boolean> operation = redisTemplate.delete(key)
                .map(count -> count > 0)
                .doOnSuccess(deleted -> {
                    if (Boolean.TRUE.equals(deleted)) {
                        log.debug("Cleared GPS history for vehicle {}", vehicleId);
                    }
                });

        return timeoutHandler.wrapWithTimeout(
                operation,
                OPERATION_CLEAR,
                "vehicleId=" + vehicleId
        ).defaultIfEmpty(false);
    }

    public Mono<Boolean> hasEnoughPoints(String vehicleId, int minPoints) {
        return getPointCount(vehicleId)
                .map(count -> count >= minPoints);
    }

    private GpsPoint deserializePoint(Object value) {
        if (value == null) {
            return null;
        }

        try {
            String json = value.toString();
            return objectMapper.readValue(json, GpsPoint.class);
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize GPS point: {}", e.getMessage());
            return null;
        }
    }
}
