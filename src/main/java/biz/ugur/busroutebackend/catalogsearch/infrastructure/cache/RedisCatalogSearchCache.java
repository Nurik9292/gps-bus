package biz.ugur.busroutebackend.catalogsearch.infrastructure.cache;

import biz.ugur.busroutebackend.catalogsearch.domain.model.CatalogObjectKind;
import biz.ugur.busroutebackend.catalogsearch.domain.model.SearchHit;
import biz.ugur.busroutebackend.catalogsearch.domain.repository.CatalogSearchCache;
import biz.ugur.busroutebackend.catalogsearch.infrastructure.config.CatalogSearchProperties;
import biz.ugur.busroutebackend.shared.infrastructure.cache.RedisKeyRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class RedisCatalogSearchCache implements CatalogSearchCache {

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final CatalogSearchProperties properties;

    public RedisCatalogSearchCache(ReactiveRedisTemplate<String, Object> redisTemplate,
                                   CatalogSearchProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public Mono<List<SearchHit>> get(String normalizedQuery, int limit) {
        return redisTemplate.opsForValue().get(key(normalizedQuery, limit))
                .map(RedisCatalogSearchCache::deserialize)
                .doOnNext(hits -> log.debug("[CATALOG_SEARCH] cache hit: q='{}' limit={} hits={}",
                        normalizedQuery, limit, hits.size()))
                .onErrorResume(err -> {
                    log.warn("[CATALOG_SEARCH] cache read failed: {}", err.getMessage());
                    return Mono.empty();
                });
    }

    @Override
    public Mono<Void> put(String normalizedQuery, int limit, List<SearchHit> hits) {
        List<Map<String, Object>> payload = hits.stream()
                .map(RedisCatalogSearchCache::serialize)
                .toList();
        return redisTemplate.opsForValue()
                .set(key(normalizedQuery, limit), payload,
                        Duration.ofSeconds(properties.getCacheTtlSeconds()))
                .onErrorResume(err -> {
                    log.warn("[CATALOG_SEARCH] cache write failed: {}", err.getMessage());
                    return Mono.just(false);
                })
                .then();
    }

    @Override
    public Mono<Long> evictAll() {
        return redisTemplate.scan(ScanOptions.scanOptions()
                        .match(RedisKeyRegistry.CatalogSearch.pattern()).count(200).build())
                .flatMap(redisTemplate::unlink)
                .reduce(0L, Long::sum)
                .onErrorResume(err -> {
                    log.warn("[CATALOG_SEARCH] cache evict failed: {}", err.getMessage());
                    return Mono.just(0L);
                });
    }

    private static String key(String normalizedQuery, int limit) {
        return RedisKeyRegistry.CatalogSearch.query(sha1(normalizedQuery), limit);
    }

    private static String sha1(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> serialize(SearchHit hit) {
        java.util.HashMap<String, Object> map = new java.util.HashMap<>();
        map.put("objectKind", hit.objectKind().name());
        map.put("objectId", hit.objectId());
        map.put("title", hit.title());
        map.put("subtitle", hit.subtitle());
        map.put("lat", hit.lat());
        map.put("lon", hit.lon());
        map.put("score", hit.score());
        map.put("source", hit.source());
        return map;
    }

    @SuppressWarnings("unchecked")
    private static List<SearchHit> deserialize(Object raw) {
        List<Map<String, Object>> rows = (List<Map<String, Object>>) raw;
        return rows.stream().map(m -> new SearchHit(
                CatalogObjectKind.fromString((String) m.get("objectKind")),
                (String) m.get("objectId"),
                (String) m.get("title"),
                (String) m.get("subtitle"),
                m.get("lat") == null ? null : ((Number) m.get("lat")).doubleValue(),
                m.get("lon") == null ? null : ((Number) m.get("lon")).doubleValue(),
                ((Number) m.get("score")).doubleValue(),
                (String) m.get("source"))).toList();
    }
}
