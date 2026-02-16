package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.PlaceNearbyQuery;
import biz.ugur.busroutebackend.place.application.dto.PlaceSearchResult;
import biz.ugur.busroutebackend.place.domain.repository.PlaceSearchRepository;
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
public class FindNearbyPlacesUseCase extends BaseUseCase<Mono<PlaceNearbyQuery>, List<PlaceSearchResult>> {

    private final PlaceSearchRepository placeSearchRepository;
    private final PlaceSearchProperties properties;

    public FindNearbyPlacesUseCase(PlaceSearchRepository placeSearchRepository,
                                   PlaceSearchProperties properties,
                                   CorrelationContextService correlationService,
                                   EventBus eventBus) {
        super(correlationService, eventBus);
        this.placeSearchRepository = placeSearchRepository;
        this.properties = properties;
    }

    @Override
    protected Mono<List<PlaceSearchResult>> process(Mono<PlaceNearbyQuery> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "place";
    }

    private Mono<List<PlaceSearchResult>> processInternal(PlaceNearbyQuery query) {
        int radius = query.radiusMeters() > 0
                ? Math.min(query.radiusMeters(), properties.getNearbyMaxRadiusMeters())
                : properties.getNearbyDefaultRadiusMeters();
        int limit = Math.min(query.limit() > 0 ? query.limit() : properties.getDefaultLimit(), properties.getMaxLimit());

        return placeSearchRepository.searchNearby(query.lat(), query.lng(), radius, query.category(), limit)
                .collectList();
    }
}
