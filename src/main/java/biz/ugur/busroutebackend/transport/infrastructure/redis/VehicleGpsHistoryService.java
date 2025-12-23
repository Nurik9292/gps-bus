package biz.ugur.busroutebackend.transport.infrastructure.redis;

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

/**
 * Service for storing and retrieving GPS history for vehicles in Redis.
 *
 * Uses Redis Lists to store the last N GPS points per vehicle.
 * Points are stored in reverse chronological order (newest first).
 *
 * Key format: gps:history:{vehicleId}
 * TTL: Configurable (default 30 minutes)
 * Max points: Configurable (default 100)
 */
@Service
@Slf4j
public class VehicleGpsHistoryService {

    private static final String KEY_PREFIX = "gps:history:";

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${business.gps-history.max-points:100}")
    private int maxPoints;

    @Value("${business.gps-history.ttl-minutes:30}")
    private int ttlMinutes;

    public VehicleGpsHistoryService(ReactiveRedisTemplate<String, Object> redisTemplate,
                                    ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Add a GPS point to the vehicle's history.
     *
     * @param vehicleId Vehicle ID
     * @param latitude Latitude
     * @param longitude Longitude
     * @param speed Speed in km/h
     * @param timestamp Timestamp of the position
     * @return Mono indicating completion
     */
    public Mono<Void> addPoint(String vehicleId, Double latitude, Double longitude,
                                Double speed, LocalDateTime timestamp) {
        if (vehicleId == null || latitude == null || longitude == null) {
            return Mono.empty();
        }

        String key = KEY_PREFIX + vehicleId;
        GpsPoint point = GpsPoint.of(latitude, longitude, speed, timestamp);

        try {
            String jsonPoint = objectMapper.writeValueAsString(point);

            return redisTemplate.opsForList().leftPush(key, jsonPoint)
                    .flatMap(size -> {
                        if (size > maxPoints) {
                            return redisTemplate.opsForList().trim(key, 0, maxPoints - 1).then();
                        }
                        return Mono.empty();
                    })
                    .then(redisTemplate.expire(key, Duration.ofMinutes(ttlMinutes)))
                    .then()
                    .doOnError(e -> log.error("Failed to add GPS point for vehicle {}: {}",
                            vehicleId, e.getMessage()));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize GPS point for vehicle {}: {}", vehicleId, e.getMessage());
            return Mono.empty();
        }
    }

    /**
     * Get GPS history for a vehicle.
     *
     * @param vehicleId Vehicle ID
     * @param limit Maximum number of points to retrieve (default: all)
     * @return Flux of GPS points in reverse chronological order (newest first)
     */
    public Flux<GpsPoint> getHistory(String vehicleId, int limit) {
        if (vehicleId == null) {
            return Flux.empty();
        }

        String key = KEY_PREFIX + vehicleId;
        int effectiveLimit = limit > 0 ? Math.min(limit, maxPoints) : maxPoints;

        return redisTemplate.opsForList().range(key, 0, effectiveLimit - 1)
                .mapNotNull(this::deserializePoint)
                .doOnError(e -> log.error("Failed to get GPS history for vehicle {}: {}",
                        vehicleId, e.getMessage()));
    }

    /**
     * Get all GPS history for a vehicle.
     *
     * @param vehicleId Vehicle ID
     * @return Flux of GPS points
     */
    public Flux<GpsPoint> getHistory(String vehicleId) {
        return getHistory(vehicleId, maxPoints);
    }

    /**
     * Get GPS history as a list.
     *
     * @param vehicleId Vehicle ID
     * @param limit Maximum number of points
     * @return Mono containing list of GPS points
     */
    public Mono<List<GpsPoint>> getHistoryList(String vehicleId, int limit) {
        return getHistory(vehicleId, limit).collectList();
    }

    /**
     * Get the count of GPS points stored for a vehicle.
     *
     * @param vehicleId Vehicle ID
     * @return Mono containing the count
     */
    public Mono<Long> getPointCount(String vehicleId) {
        if (vehicleId == null) {
            return Mono.just(0L);
        }

        String key = KEY_PREFIX + vehicleId;
        return redisTemplate.opsForList().size(key)
                .defaultIfEmpty(0L);
    }

    /**
     * Clear GPS history for a vehicle.
     *
     * @param vehicleId Vehicle ID
     * @return Mono indicating completion
     */
    public Mono<Boolean> clearHistory(String vehicleId) {
        if (vehicleId == null) {
            return Mono.just(false);
        }

        String key = KEY_PREFIX + vehicleId;
        return redisTemplate.delete(key)
                .map(count -> count > 0)
                .doOnSuccess(deleted -> {
                    if (Boolean.TRUE.equals(deleted)) {
                        log.debug("Cleared GPS history for vehicle {}", vehicleId);
                    }
                });
    }

    /**
     * Check if a vehicle has enough GPS points for route detection.
     *
     * @param vehicleId Vehicle ID
     * @param minPoints Minimum required points
     * @return Mono containing true if enough points exist
     */
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
