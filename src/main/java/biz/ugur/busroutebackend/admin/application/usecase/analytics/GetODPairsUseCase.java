package biz.ugur.busroutebackend.admin.application.usecase.analytics;

import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.analytics.ODPairsResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.analytics.ODPairsResponse.ODPairResponse;
import biz.ugur.busroutebackend.routing.domain.repository.RoutingAnalyticsRepository;
import biz.ugur.busroutebackend.shared.infrastructure.cache.RedisKeyRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@RequiredArgsConstructor
public class GetODPairsUseCase {

    private final RoutingAnalyticsRepository analyticsRepository;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Mono<ODPairsResponse> execute(int limit) {
        String cacheKey = RedisKeyRegistry.Analytics.routingOdPairs(limit);

        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(this::deserialize)
                .switchIfEmpty(Mono.defer(() -> fetchAndCache(limit, cacheKey)));
    }

    private Mono<ODPairsResponse> fetchAndCache(int limit, String cacheKey) {
        return analyticsRepository.getPopularODPairs(limit)
                .map(p -> new ODPairResponse(p.fromLat(), p.fromLon(), p.toLat(), p.toLon(), p.tripCount(), p.avgOptions()))
                .collectList()
                .map(ODPairsResponse::new)
                .flatMap(response -> cacheAndReturn(cacheKey, response));
    }

    private Mono<ODPairsResponse> deserialize(String json) {
        try {
            return Mono.just(objectMapper.readValue(json, ODPairsResponse.class));
        } catch (Exception e) {
            log.warn("Failed to deserialize cached OD pairs: {}", e.getMessage());
            return Mono.empty();
        }
    }

    private Mono<ODPairsResponse> cacheAndReturn(String cacheKey, ODPairsResponse data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return redisTemplate.opsForValue()
                    .set(cacheKey, json, RedisKeyRegistry.Analytics.OD_PAIRS_TTL)
                    .thenReturn(data)
                    .onErrorReturn(data);
        } catch (Exception e) {
            log.warn("Failed to cache OD pairs: {}", e.getMessage());
            return Mono.just(data);
        }
    }
}
