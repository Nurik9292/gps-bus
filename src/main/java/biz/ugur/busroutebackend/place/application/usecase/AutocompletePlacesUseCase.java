package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.PlaceAutocompleteResult;
import biz.ugur.busroutebackend.place.application.dto.PlaceSearchQuery;
import biz.ugur.busroutebackend.place.domain.repository.PlaceSearchRepository;
import biz.ugur.busroutebackend.place.infrastructure.cache.PlaceSearchCacheService;
import biz.ugur.busroutebackend.place.infrastructure.config.PlaceSearchProperties;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class AutocompletePlacesUseCase extends BaseUseCase<Mono<PlaceSearchQuery>, List<PlaceAutocompleteResult>> {

    private final PlaceSearchRepository placeSearchRepository;
    private final PlaceSearchCacheService cacheService;
    private final PlaceSearchProperties properties;

    public AutocompletePlacesUseCase(PlaceSearchRepository placeSearchRepository,
                                     PlaceSearchCacheService cacheService,
                                     PlaceSearchProperties properties,
                                     CorrelationContextService correlationService,
                                     EventBus eventBus) {
        super(correlationService, eventBus);
        this.placeSearchRepository = placeSearchRepository;
        this.cacheService = cacheService;
        this.properties = properties;
    }

    @Override
    protected Mono<List<PlaceAutocompleteResult>> process(Mono<PlaceSearchQuery> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "place";
    }

    private Mono<List<PlaceAutocompleteResult>> processInternal(PlaceSearchQuery query) {
        int limit = Math.min(query.limit() > 0 ? query.limit() : 10, properties.getMaxLimit());
        String cacheKey = PlaceSearchCacheService.buildAutocompleteCacheKey(query.query(), query.cityId(), limit);

        return cacheService.getCachedAutocomplete(cacheKey)
                .switchIfEmpty(
                        placeSearchRepository.autocomplete(query.query(), query.cityId(), limit)
                                .collectList()
                                .flatMap(results -> cacheService.cacheAutocompleteResults(cacheKey, results).thenReturn(results))
                );
    }
}
