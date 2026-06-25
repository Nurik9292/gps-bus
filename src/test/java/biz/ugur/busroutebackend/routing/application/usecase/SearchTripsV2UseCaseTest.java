package biz.ugur.busroutebackend.routing.application.usecase;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.interfaces.rest.routing.V1.request.TripSearchRequest;
import biz.ugur.busroutebackend.interfaces.rest.routing.V2.response.TripSearchResponseV2;
import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.response.ResponseBuilderV2;
import biz.ugur.busroutebackend.routing.domain.exceptions.TripPlanningException;
import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripSearchCriteria;
import biz.ugur.busroutebackend.routing.infrastructure.config.RouteSearchConfig;
import biz.ugur.busroutebackend.routing.infrastructure.config.SearchContextFactory;
import biz.ugur.busroutebackend.routing.infrastructure.services.ParallelRouteSearchService;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchTripsV2UseCaseTest {

    private static final Coordinates A = Coordinates.of(37.9601, 58.3261);
    private static final Coordinates B = Coordinates.of(37.9701, 58.3361);

    private final ParallelRouteSearchService parallelSearchService = mock(ParallelRouteSearchService.class);
    private final ResponseBuilderV2 responseBuilder = mock(ResponseBuilderV2.class);
    private final SearchContextFactory contextFactory = mock(SearchContextFactory.class);
    private final EventBus eventBus = mock(EventBus.class);

    private final SearchTripsV2UseCase useCase = new SearchTripsV2UseCase(
            parallelSearchService, responseBuilder, contextFactory,
            new RouteSearchConfig(), new CorrelationContextService(), eventBus);

    private TripSearchRequest request(Double fromLat, Double fromLon) {
        TripSearchRequest request = new TripSearchRequest();
        if (fromLat != null) {
            TripSearchRequest.LocationDTO from = new TripSearchRequest.LocationDTO();
            from.setLatitude(fromLat);
            from.setLongitude(fromLon);
            request.setFrom(from);
        }
        TripSearchRequest.LocationDTO to = new TripSearchRequest.LocationDTO();
        to.setLatitude(37.9701);
        to.setLongitude(58.3361);
        request.setTo(to);
        return request;
    }

    @Test
    void returnsSuccessResponseForValidRequest() {
        TripSearchCriteria criteria = TripSearchCriteria.defaultCriteria();
        SearchContext context = SearchContext.of(A, B, criteria);
        TripPlan plan = TripPlan.create(A, B, criteria);

        when(contextFactory.createFromRequest(any(), any())).thenReturn(context);
        when(parallelSearchService.searchAllRoutes(any())).thenReturn(Mono.just(plan));
        when(responseBuilder.createSuccessResponse(any(), any()))
                .thenReturn(Mono.just(TripSearchResponseV2.success("ok", List.of())));

        StepVerifier.create(useCase.execute(Mono.just(request(37.9601, 58.3261))))
                .assertNext(response -> {
                    org.assertj.core.api.Assertions.assertThat(response.status()).isEqualTo("success");
                })
                .verifyComplete();
    }

    @Test
    void returnsErrorResponseWhenOriginMissing() {
        when(responseBuilder.createErrorResponse(
                TripPlanningException.PlanningErrorType.MISSING_LOCATION, null))
                .thenReturn(Mono.just(TripSearchResponseV2.error("missing", "MISSING_LOCATION")));

        StepVerifier.create(useCase.execute(Mono.just(request(null, null))))
                .assertNext(response -> {
                    org.assertj.core.api.Assertions.assertThat(response.status()).isEqualTo("error");
                    org.assertj.core.api.Assertions.assertThat(response.errorType()).isEqualTo("MISSING_LOCATION");
                })
                .verifyComplete();
    }
}
