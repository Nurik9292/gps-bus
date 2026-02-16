package biz.ugur.busroutebackend.place.infrastructure.cache;

import biz.ugur.busroutebackend.place.application.dto.PlaceAutocompleteResult;
import biz.ugur.busroutebackend.place.application.dto.PlaceSearchResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class PlaceSearchCacheService {

    private static final Duration SEARCH_TTL = Duration.ofMinutes(5);
    private static final Duration AUTOCOMPLETE_TTL = Duration.ofMinutes(3);
    private static final String SEARCH_PREFIX = "place:search:";
    private static final String AUTOCOMPLETE_PREFIX = "place:autocomplete:";
    private static final String NEARBY_PREFIX = "place:nearby:";

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PlaceSearchCacheService(ReactiveStringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public Mono<List<PlaceSearchResult>> getCachedSearch(String cacheKey) {
        return redisTemplate.opsForValue().get(SEARCH_PREFIX + cacheKey)
                .flatMap(json -> {
                    try {
                        List<PlaceSearchResult> results = objectMapper.readValue(json, new TypeReference<>() {});
                        return Mono.just(results);
                    } catch (Exception e) {
                        log.trace("Cache deserialization error", e);
                        return Mono.empty();
                    }
                });
    }

    public Mono<Void> cacheSearchResults(String cacheKey, List<PlaceSearchResult> results) {
        try {
            String json = objectMapper.writeValueAsString(results);
            return redisTemplate.opsForValue()
                    .set(SEARCH_PREFIX + cacheKey, json, SEARCH_TTL)
                    .then();
        } catch (Exception e) {
            log.trace("Cache serialization error", e);
            return Mono.empty();
        }
    }

    public Mono<List<PlaceAutocompleteResult>> getCachedAutocomplete(String cacheKey) {
        return redisTemplate.opsForValue().get(AUTOCOMPLETE_PREFIX + cacheKey)
                .flatMap(json -> {
                    try {
                        List<PlaceAutocompleteResult> results = objectMapper.readValue(json, new TypeReference<>() {});
                        return Mono.just(results);
                    } catch (Exception e) {
                        log.trace("Cache deserialization error", e);
                        return Mono.empty();
                    }
                });
    }

    public Mono<Void> cacheAutocompleteResults(String cacheKey, List<PlaceAutocompleteResult> results) {
        try {
            String json = objectMapper.writeValueAsString(results);
            return redisTemplate.opsForValue()
                    .set(AUTOCOMPLETE_PREFIX + cacheKey, json, AUTOCOMPLETE_TTL)
                    .then();
        } catch (Exception e) {
            log.trace("Cache serialization error", e);
            return Mono.empty();
        }
    }

    public static String buildSearchCacheKey(String query, String cityId, String category, int limit) {
        return String.format("%s:%s:%s:%d",
                query != null ? query.toLowerCase().trim() : "",
                cityId != null ? cityId : "",
                category != null ? category : "",
                limit);
    }

    public static String buildAutocompleteCacheKey(String query, String cityId, int limit) {
        return String.format("%s:%s:%d",
                query != null ? query.toLowerCase().trim() : "",
                cityId != null ? cityId : "",
                limit);
    }
}
