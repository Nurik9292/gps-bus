package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.GeocodingResult;
import biz.ugur.busroutebackend.place.application.dto.PlaceSearchQuery;
import biz.ugur.busroutebackend.place.application.dto.PlaceSearchResult;
import biz.ugur.busroutebackend.place.domain.repository.PlaceSearchRepository;
import biz.ugur.busroutebackend.place.domain.services.GeocodingService;
import biz.ugur.busroutebackend.place.domain.services.StreetAliasResolver;
import biz.ugur.busroutebackend.place.infrastructure.cache.PlaceSearchCacheService;
import biz.ugur.busroutebackend.place.infrastructure.config.PlaceSearchProperties;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class SearchPlacesUseCase extends BaseUseCase<Mono<PlaceSearchQuery>, List<PlaceSearchResult>> {

    private static final int FALLBACK_THRESHOLD = 3;

    private final PlaceSearchRepository placeSearchRepository;
    private final StreetAliasResolver streetAliasResolver;
    private final GeocodingService geocodingService;
    private final PlaceSearchCacheService cacheService;
    private final PlaceSearchProperties properties;

    public SearchPlacesUseCase(PlaceSearchRepository placeSearchRepository,
                               StreetAliasResolver streetAliasResolver,
                               GeocodingService geocodingService,
                               PlaceSearchCacheService cacheService,
                               PlaceSearchProperties properties,
                               CorrelationContextService correlationService,
                               EventBus eventBus) {
        super(correlationService, eventBus);
        this.placeSearchRepository = placeSearchRepository;
        this.streetAliasResolver = streetAliasResolver;
        this.geocodingService = geocodingService;
        this.cacheService = cacheService;
        this.properties = properties;
    }

    @Override
    protected Mono<List<PlaceSearchResult>> process(Mono<PlaceSearchQuery> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "place";
    }

    private Mono<List<PlaceSearchResult>> processInternal(PlaceSearchQuery query) {
        int limit = Math.min(query.limit() > 0 ? query.limit() : properties.getDefaultLimit(), properties.getMaxLimit());
        String cacheKey = PlaceSearchCacheService.buildSearchCacheKey(query.query(), query.cityId(), query.category(), limit);

        return cacheService.getCachedSearch(cacheKey)
                .switchIfEmpty(
                        streetAliasResolver.resolveStreetAliases(query.query(), query.cityId())
                                .flatMap(resolvedQuery -> searchWithFallback(resolvedQuery, query.cityId(), query.category(), limit))
                                .flatMap(results -> cacheService.cacheSearchResults(cacheKey, results).thenReturn(results))
                );
    }

    private Mono<List<PlaceSearchResult>> searchWithFallback(String query, String cityId, String category, int limit) {
        return placeSearchRepository.search(query, cityId, category, limit, properties.getSimilarityThreshold())
                .collectList()
                .flatMap(dbResults -> {
                    if (dbResults.size() >= FALLBACK_THRESHOLD) {
                        log.debug("DB search for '{}': {} results — skipping Nominatim", query, dbResults.size());
                        return Mono.just(dbResults);
                    }
                    log.debug("DB search for '{}': {} results — querying Nominatim", query, dbResults.size());
                    int remaining = limit - dbResults.size();
                    return geocodingService.search(query, remaining)
                            .map(nominatimResults -> mergeResults(dbResults, nominatimResults));
                });
    }

    private List<PlaceSearchResult> mergeResults(List<PlaceSearchResult> dbResults, List<GeocodingResult> nominatimResults) {
        if (nominatimResults.isEmpty()) {
            return dbResults;
        }
        List<PlaceSearchResult> merged = new ArrayList<>(dbResults);
        for (GeocodingResult geo : nominatimResults) {
            if (isDuplicate(dbResults, geo)) {
                continue;
            }
            merged.add(toPlaceSearchResult(geo));
        }
        return merged;
    }

    private boolean isDuplicate(List<PlaceSearchResult> dbResults, GeocodingResult geo) {
        if (geo.latitude() == null || geo.longitude() == null) {
            return false;
        }
        return dbResults.stream().anyMatch(db ->
                db.latitude() != null && db.longitude() != null
                        && db.latitude().subtract(geo.latitude()).abs().doubleValue() < 0.0005
                        && db.longitude().subtract(geo.longitude()).abs().doubleValue() < 0.0005
        );
    }

    private PlaceSearchResult toPlaceSearchResult(GeocodingResult geo) {
        return new PlaceSearchResult(
                null,
                geo.displayName(),
                null,
                null,
                null,
                geo.address(),
                null,
                geo.latitude(),
                geo.longitude(),
                geo.city(),
                List.of(),
                0.3
        );
    }
}
