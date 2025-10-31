package biz.ugur.busroutebackend.routing.infrastructure.services.cache;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripSearchCriteria;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RouteSearchCacheService {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final Duration NEARBY_STOPS_TTL = Duration.ofMinutes(5);
    private static final Duration SEARCH_RESULTS_TTL = Duration.ofMinutes(10);
    private static final Duration CONNECTION_CACHE_TTL = Duration.ofHours(1);

    public Mono<List<BusStopId>> getCachedNearbyStops(Coordinates location, double radiusKm) {
        String cacheKey = buildNearbyStopsCacheKey(location, radiusKm);

        return redisTemplate.opsForValue()
                .get(cacheKey)
                .cast(String.class)
                .flatMap(jsonString -> {
                    try {
                        TypeReference<List<String>> typeRef = new TypeReference<>() {};
                        List<String> stopIds = objectMapper.readValue(jsonString, typeRef);
                        List<BusStopId> result = stopIds.stream()
                                .map(BusStopId::of)
                                .collect(Collectors.toList());
                        log.debug("✅ Cache hit for nearby stops: {} ({} stops)", cacheKey, result.size());
                        return Mono.just(result);
                    } catch (Exception e) {
                        log.warn("Failed to deserialize cached nearby stops: {}", e.getMessage());
                        return redisTemplate.delete(cacheKey).then(Mono.empty());
                    }
                })
                .onErrorResume(error -> {
                    log.debug("❌ Cache miss for nearby stops: {}", error.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Boolean> cacheNearbyStops(Coordinates location, double radiusKm, List<BusStop> stops) {
        String cacheKey = buildNearbyStopsCacheKey(location, radiusKm);

        try {
            List<String> stopIds = stops.stream()
                    .map(stop -> stop.getId().getValue())
                    .toList();

            String jsonString = objectMapper.writeValueAsString(stopIds);

            return redisTemplate.opsForValue()
                    .set(cacheKey, jsonString, NEARBY_STOPS_TTL)
                    .doOnSuccess(success -> {
                        if (Boolean.TRUE.equals(success)) {
                            log.debug("✅ Cached {} nearby stops with key: {}", stopIds.size(), cacheKey);
                        }
                    })
                    .onErrorResume(error -> {
                        log.warn("Failed to cache nearby stops: {}", error.getMessage());
                        return Mono.just(false);
                    });
        } catch (Exception e) {
            log.error("Failed to serialize nearby stops: {}", e.getMessage());
            return Mono.just(false);
        }
    }

    public <T> Mono<T> getCached(String key, Class<T> type) {
        return redisTemplate.opsForValue()
                .get(key)
                .cast(type)
                .doOnNext(value -> log.debug("Cache hit: {}", key))
                .onErrorResume(error -> {
                    log.debug("Cache miss: {}", error.getMessage());
                    return Mono.empty();
                });
    }

    public <T> Mono<Boolean> cache(String key, T value, Duration ttl) {
        return redisTemplate.opsForValue()
                .set(key, value, ttl)
                .doOnSuccess(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        log.debug("Cached value with key: {} (TTL: {})", key, ttl);
                    }
                })
                .onErrorResume(error -> {
                    log.warn("Failed to cache value: {}", error.getMessage());
                    return Mono.just(false);
                });
    }

    public Mono<Boolean> cacheStopsConnection(BusStopId stop1, BusStopId stop2, boolean connected) {
        String cacheKey = buildConnectionCacheKey(stop1, stop2);
        return cache(cacheKey, connected, CONNECTION_CACHE_TTL);
    }

    public Mono<Boolean> getCachedConnection(BusStopId stop1, BusStopId stop2) {
        String cacheKey = buildConnectionCacheKey(stop1, stop2);
        return getCached(cacheKey, Boolean.class);
    }

    public String buildNearbyStopsCacheKey(Coordinates location, double radiusKm) {
        return String.format("nearby_stops:%.6f:%.6f:%.1f",
                location.getLatitudeAsDouble(),
                location.getLongitudeAsDouble(),
                radiusKm);
    }

    public String buildSearchResultsCacheKey(SearchContext context) {
        Coordinates from = context.fromLocation();
        Coordinates to = context.toLocation();
        TripSearchCriteria criteria = context.searchCriteria();

        return String.format("trip_search:%.4f:%.4f:%.4f:%.4f:%d:%d:%s:%s",
                from.getLatitudeAsDouble(), from.getLongitudeAsDouble(),
                to.getLatitudeAsDouble(), to.getLongitudeAsDouble(),
                criteria.getMaxWalkingDistanceMeters(),
                criteria.getMaxTransfers(),
                criteria.isPrioritizeSpeed(),
                criteria.isPrioritizeFewerTransfers());
    }

    private String buildConnectionCacheKey(BusStopId stop1, BusStopId stop2) {
        return String.format("stops_connected:%s:%s", stop1.getValue(), stop2.getValue());
    }

    public Duration getSearchResultsTtl() {
        return SEARCH_RESULTS_TTL;
    }
}
