package biz.ugur.busroutebackend.place.infrastructure.services;

import biz.ugur.busroutebackend.place.domain.repository.StreetAliasRepository;
import biz.ugur.busroutebackend.place.domain.repository.StreetRepository;
import biz.ugur.busroutebackend.place.domain.services.StreetAliasResolver;
import biz.ugur.busroutebackend.place.domain.valueobjects.StreetId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class StreetAliasResolverImpl implements StreetAliasResolver {

    private static final String CACHE_KEY = "place:street:alias:map";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final StreetAliasRepository streetAliasRepository;
    private final StreetRepository streetRepository;
    private final ReactiveStringRedisTemplate redisTemplate;

    public StreetAliasResolverImpl(StreetAliasRepository streetAliasRepository,
                                   StreetRepository streetRepository,
                                   ReactiveStringRedisTemplate redisTemplate) {
        this.streetAliasRepository = streetAliasRepository;
        this.streetRepository = streetRepository;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<String> resolveStreetAliases(String query, String cityId) {
        if (query == null || query.isBlank()) {
            return Mono.just("");
        }

        return loadAliasMap()
                .map(aliasMap -> {
                    String resolved = query;
                    for (Map.Entry<String, String> entry : aliasMap.entrySet()) {
                        String alias = entry.getKey();
                        String officialName = entry.getValue();
                        if (resolved.toLowerCase().contains(alias.toLowerCase())) {
                            resolved = resolved.replaceAll("(?i)" + java.util.regex.Pattern.quote(alias), officialName);
                        }
                    }
                    return resolved;
                });
    }

    private Mono<Map<String, String>> loadAliasMap() {
        return redisTemplate.opsForHash().entries(CACHE_KEY)
                .collectMap(
                        entry -> entry.getKey().toString(),
                        entry -> entry.getValue().toString()
                )
                .flatMap(cached -> {
                    if (!cached.isEmpty()) {
                        return Mono.just(cached);
                    }
                    return buildAndCacheAliasMap();
                });
    }

    private Mono<Map<String, String>> buildAndCacheAliasMap() {
        Map<String, String> aliasMap = new ConcurrentHashMap<>();

        return streetAliasRepository.findAllWithStreetNames()
                .flatMap(streetAlias ->
                        streetRepository.findById(StreetId.of(streetAlias.getStreetId()))
                                .doOnNext(street -> aliasMap.put(streetAlias.getAlias(), street.getName()))
                )
                .then(Mono.defer(() -> {
                    if (aliasMap.isEmpty()) {
                        return Mono.just(aliasMap);
                    }
                    return redisTemplate.opsForHash()
                            .putAll(CACHE_KEY, new ConcurrentHashMap<>(aliasMap))
                            .then(redisTemplate.expire(CACHE_KEY, CACHE_TTL))
                            .thenReturn(aliasMap);
                }));
    }
}
