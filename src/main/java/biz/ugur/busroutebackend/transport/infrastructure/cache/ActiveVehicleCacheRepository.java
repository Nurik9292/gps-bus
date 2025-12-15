package biz.ugur.busroutebackend.transport.infrastructure.cache;

import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
@Slf4j
public class ActiveVehicleCacheRepository {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;


    public Flux<VehiclePositionDTO> getCached(String key) {
        return redisTemplate.opsForList()
                .range(key, 0, -1)
                .mapNotNull(obj -> {
                    try {
                        if (obj instanceof VehiclePositionDTO) {
                            return (VehiclePositionDTO) obj;
                        } else {
                            return objectMapper.convertValue(obj, VehiclePositionDTO.class);
                        }
                    } catch (Exception e) {
                        log.warn("Failed to deserialize cached vehicle from key {}: {} - Object type: {}",
                                key, e.getMessage(), obj != null ? obj.getClass().getName() : "null");
                        return null;
                    }
                })
                .doOnNext(v -> log.trace("Loaded cached vehicle {} from {}", v.getLicensePlate(), key));
    }

    public Mono<Void> cache(String key, Flux<VehiclePositionDTO> vehicles, Duration ttl) {
        return vehicles.collectList()
                .flatMap(list -> {
                    if (list.isEmpty()) {
                        log.debug("Skipping cache for empty list at key {}", key);
                        return Mono.empty();
                    }

                    return redisTemplate.delete(key)
                            .then(redisTemplate.opsForList().rightPushAll(key, list.toArray()))
                            .then(redisTemplate.expire(key, ttl))
                            .doOnSuccess(v -> log.debug("Cached {} vehicles for key {} with TTL {}",
                                    list.size(), key, ttl))
                            .doOnError(error -> log.warn("Failed to cache vehicles for key {}: {}",
                                    key, error.getMessage()));
                })
                .then();
    }
}