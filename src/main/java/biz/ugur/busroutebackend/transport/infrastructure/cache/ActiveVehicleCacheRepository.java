package biz.ugur.busroutebackend.transport.infrastructure.cache;

import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionDTO;
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


    public Flux<VehiclePositionDTO> getCached(String key) {
        return redisTemplate.opsForList()
                .range(key, 0, -1)
                .cast(VehiclePositionDTO.class)
                .doOnNext(v -> log.trace("Loaded cached vehicle {} from {}", v.getLicensePlate(), key));

    }

    public Mono<Void> cache(String key, Flux<VehiclePositionDTO> vehicles, Duration ttl) {
        return vehicles.collectList()
                .flatMapMany(list -> redisTemplate.opsForList().rightPushAll(key, list))
                .then(redisTemplate.expire(key, ttl))
                .then()
                .doOnSuccess(v -> log.debug("Cached vehicles for key {} with TTL {}", key, ttl));
    }
}