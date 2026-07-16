package biz.ugur.busroutebackend.place.application.usecase;

import biz.ugur.busroutebackend.place.application.dto.GeocodingResult;
import biz.ugur.busroutebackend.place.application.dto.PlaceSearchResult;
import biz.ugur.busroutebackend.place.domain.services.GeocodingService;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class GeocodeFallbackUseCase extends BaseUseCase<Mono<GeocodeFallbackUseCase.Query>, List<PlaceSearchResult>> {

    public record Query(String q, int limit) {
    }

    static final double GEOCODED_SCORE = 0.3;

    private final GeocodingService geocodingService;

    public GeocodeFallbackUseCase(GeocodingService geocodingService,
                                  CorrelationContextService correlationService,
                                  EventBus eventBus) {
        super(correlationService, eventBus);
        this.geocodingService = geocodingService;
    }

    @Override
    protected Mono<List<PlaceSearchResult>> process(Mono<Query> request) {
        return request.flatMap(query -> geocodingService.search(query.q(), query.limit())
                .map(GeocodeFallbackUseCase::toPlaceSearchResults));
    }

    private static List<PlaceSearchResult> toPlaceSearchResults(List<GeocodingResult> geoResults) {
        return geoResults.stream()
                .map(geo -> new PlaceSearchResult(
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
                        List.<String>of(),
                        GEOCODED_SCORE))
                .toList();
    }

    @Override
    protected String getBoundContext() {
        return "place";
    }
}
