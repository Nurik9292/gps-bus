package biz.ugur.busroutebackend.transport.infrastructure.cache;

import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Repository
@RequiredArgsConstructor
@Slf4j
public class ActiveVehicleCacheRepository {

    private static final TypeReference<List<VehiclePositionDTO>> VEHICLE_LIST_TYPE = new TypeReference<>() {};

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;


    public Flux<VehiclePositionDTO> getCached(String key) {
        return redisTemplate.opsForValue().get(key)
                .flatMapMany(this::toVehicleFlux)
                .onErrorResume(error -> {
                    log.warn("Failed to read cached vehicles from key {}: {}", key, error.getMessage());
                    return Flux.empty();
                })
                .doOnNext(v -> log.trace("Loaded cached vehicle {} from {}", v.getLicensePlate(), key));
    }

    private Flux<VehiclePositionDTO> toVehicleFlux(Object cached) {
        List<VehiclePositionDTO> vehicles = objectMapper.convertValue(cached, VEHICLE_LIST_TYPE);
        return Flux.fromIterable(vehicles);
    }

    public Mono<Void> cache(String key, Flux<VehiclePositionDTO> vehicles, Duration ttl) {
        return vehicles.collectList()
                .flatMap(list -> {
                    if (list.isEmpty()) {
                        log.debug("Skipping cache for empty list at key {}", key);
                        return Mono.empty();
                    }

                    return redisTemplate.opsForValue().set(key, list, ttl)
                            .doOnSuccess(v -> log.debug("Cached {} vehicles for key {} with TTL {}",
                                    list.size(), key, ttl))
                            .doOnError(error -> log.warn("Failed to cache vehicles for key {}: {}",
                                    key, error.getMessage()));
                })
                .then();
    }
}
