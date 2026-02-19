package biz.ugur.busroutebackend.admin.application.usecase.analytics;

import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.analytics.HeatmapResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.analytics.HeatmapResponse.HeatmapPointResponse;
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
public class GetHeatmapDataUseCase {

    private final RoutingAnalyticsRepository analyticsRepository;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Mono<HeatmapResponse> execute(String pointType) {
        String cacheKey = RedisKeyRegistry.Analytics.routingHeatmap(pointType);

        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(this::deserialize)
                .switchIfEmpty(Mono.defer(() -> fetchAndCache(pointType, cacheKey)));
    }

    private Mono<HeatmapResponse> fetchAndCache(String pointType, String cacheKey) {
        return analyticsRepository.getHeatmapData(pointType)
                .map(p -> new HeatmapPointResponse(p.lat(), p.lon(), p.searchCount()))
                .collectList()
                .map(points -> new HeatmapResponse(points, pointType))
                .flatMap(response -> cacheAndReturn(cacheKey, response));
    }

    private Mono<HeatmapResponse> deserialize(String json) {
        try {
            return Mono.just(objectMapper.readValue(json, HeatmapResponse.class));
        } catch (Exception e) {
            log.warn("Failed to deserialize cached heatmap: {}", e.getMessage());
            return Mono.empty();
        }
    }

    private Mono<HeatmapResponse> cacheAndReturn(String cacheKey, HeatmapResponse data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return redisTemplate.opsForValue()
                    .set(cacheKey, json, RedisKeyRegistry.Analytics.HEATMAP_TTL)
                    .thenReturn(data)
                    .onErrorReturn(data);
        } catch (Exception e) {
            log.warn("Failed to cache heatmap: {}", e.getMessage());
            return Mono.just(data);
        }
    }
}
