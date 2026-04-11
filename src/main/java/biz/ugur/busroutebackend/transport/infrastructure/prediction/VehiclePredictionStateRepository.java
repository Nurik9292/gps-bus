package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.shared.infrastructure.cache.RedisKeyRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Objects;


@Repository
@Slf4j
public class VehiclePredictionStateRepository {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public VehiclePredictionStateRepository(ReactiveRedisTemplate<String, Object> redisTemplate,
                                             ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Mono<Void> save(VehiclePredictionState state) {
        String key = RedisKeyRegistry.Prediction.state(state.getVehicleId());
        VehiclePredictionState toSave = state.toBuilder()
                .routeCoordinates(null)
                .build();
        return redisTemplate.opsForValue()
                .set(key, toSave, RedisKeyRegistry.Prediction.STATE_TTL)
                .then()
                .onErrorResume(e -> {
                    log.debug("Failed to persist prediction state for {}: {}", state.getVehicleId(), e.getMessage());
                    return Mono.empty();
                });
    }

  
    public Flux<VehiclePredictionState> loadAll() {
        return redisTemplate.keys(RedisKeyRegistry.Prediction.statePattern())
                .flatMap(key -> redisTemplate.opsForValue().get(key))
                .filter(Objects::nonNull)
                .flatMap(obj -> {
                    try {
                        VehiclePredictionState state = objectMapper.convertValue(obj, VehiclePredictionState.class);
                        return Flux.just(state);
                    } catch (Exception e) {
                        log.debug("Failed to deserialize prediction state: {}", e.getMessage());
                        return Flux.empty();
                    }
                })
                .onErrorResume(e -> {
                    log.warn("Failed to load prediction states from Redis: {}", e.getMessage());
                    return Flux.empty();
                });
    }

    public Mono<Void> delete(String vehicleId) {
        return redisTemplate.delete(RedisKeyRegistry.Prediction.state(vehicleId))
                .then()
                .onErrorResume(e -> Mono.empty());
    }
}
