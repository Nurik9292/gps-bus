package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.transport.domain.repository.DeviceRouteMappingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;


@Repository
@Slf4j
public class RedisDeviceRouteMappingRepository implements DeviceRouteMappingRepository {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private static final String DEVICE_ROUTE_MAPPING_KEY = "device:route:mapping";
    private static final Duration CACHE_TTL = Duration.ofHours(25);

    public RedisDeviceRouteMappingRepository(ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Map<String, String>> getMapping() {
        return redisTemplate.opsForValue()
                .get(DEVICE_ROUTE_MAPPING_KEY)
                .map(value -> {
                    if (value instanceof Map<?, ?> rawMap) {
                        Map<String, String> safeMap = new HashMap<>(rawMap.size());
                        rawMap.forEach((k, v) ->
                                safeMap.put(String.valueOf(k), String.valueOf(v))
                        );
                        return safeMap;
                    }
                    return Map.<String, String>of();
                })
                .switchIfEmpty(Mono.just(Map.of()))
                .doOnNext(mapping -> log.trace(
                        "Retrieved device-route mapping with {} entries", mapping.size()
                ));
    }

    @Override
    public Mono<Void> updateMapping(Map<String, String> mapping) {
        log.info("Updating device-route mapping cache with {} entries", mapping.size());

        return redisTemplate.opsForValue()
                .set(DEVICE_ROUTE_MAPPING_KEY, mapping, CACHE_TTL)
                .then()
                .doOnSuccess(unused -> log.debug("Device-route mapping cache updated successfully"))
                .doOnError(error -> log.error("Failed to update device-route mapping cache", error));
    }
}
