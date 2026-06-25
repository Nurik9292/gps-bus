package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.dto.SearchResult;
import biz.ugur.busroutebackend.routing.application.factory.TripOptionFactory;
import biz.ugur.busroutebackend.routing.domain.enums.SegmentType;
import biz.ugur.busroutebackend.routing.domain.services.WalkingRouteService;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripSearchCriteria;
import biz.ugur.busroutebackend.routing.infrastructure.config.TripScoringProperties;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WalkingOnlyRouteSearchServiceTest {

    private static final Coordinates FROM = Coordinates.of(37.89410, 58.38620);
    private static final Coordinates TO = Coordinates.of(37.89600, 58.38800);

    private final WalkingRouteService walkingRouteService = mock(WalkingRouteService.class);
    private final WalkingOnlyRouteSearchService service = new WalkingOnlyRouteSearchService(
            new DistanceCalculationService(),
            walkingRouteService,
            new TripOptionFactory(new TripScoringProperties()));

    @Test
    void walkingOnlyHasStraightLineGeometryWhenOsrmEmpty() {
        when(walkingRouteService.getWalkingRoute(any(), any()))
                .thenReturn(Mono.just(WalkingRouteService.WalkingRouteResult.EMPTY));

        SearchContext context = SearchContext.of(FROM, TO, TripSearchCriteria.defaultCriteria());

        StepVerifier.create(service.search(context))
                .assertNext(result -> {
                    SearchResult searchResult = result;
                    assertThat(searchResult.getOptions()).hasSize(1);
                    TripOption option = searchResult.getOptions().getFirst();
                    RouteSegment walk = option.getRouteSegments().getFirst();
                    assertThat(walk.getType()).isEqualTo(SegmentType.WALKING);
                    assertThat(walk.getWalkingGeometry()).isNotNull();
                    assertThat(walk.getWalkingGeometry()).containsExactly(
                            java.util.List.of(FROM.getLatitudeAsDouble(), FROM.getLongitudeAsDouble()),
                            java.util.List.of(TO.getLatitudeAsDouble(), TO.getLongitudeAsDouble()));
                })
                .verifyComplete();
    }
}
