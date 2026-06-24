package biz.ugur.busroutebackend.routing.application.usecase;

import biz.ugur.busroutebackend.interfaces.rest.routing.V1.request.TripSearchRequest;
import biz.ugur.busroutebackend.interfaces.rest.routing.V2.response.TripSearchResponseV2;
import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.response.ResponseBuilderV2;
import biz.ugur.busroutebackend.routing.domain.exceptions.TripPlanningException;
import biz.ugur.busroutebackend.routing.infrastructure.config.RouteSearchConfig;
import biz.ugur.busroutebackend.routing.infrastructure.config.SearchContextFactory;
import biz.ugur.busroutebackend.routing.infrastructure.services.ParallelRouteSearchService;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class SearchTripsV2UseCase extends BaseUseCase<Mono<TripSearchRequest>, TripSearchResponseV2> {

    private final ParallelRouteSearchService parallelSearchService;
    private final ResponseBuilderV2 responseBuilder;
    private final SearchContextFactory contextFactory;
    private final RouteSearchConfig config;

    public SearchTripsV2UseCase(ParallelRouteSearchService parallelSearchService,
                                ResponseBuilderV2 responseBuilder,
                                SearchContextFactory contextFactory,
                                RouteSearchConfig config,
                                CorrelationContextService correlationService,
                                EventBus eventBus) {
        super(correlationService, eventBus);
        this.parallelSearchService = parallelSearchService;
        this.responseBuilder = responseBuilder;
        this.contextFactory = contextFactory;
        this.config = config;
    }

    @Override
    protected Mono<TripSearchResponseV2> process(Mono<TripSearchRequest> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "routing";
    }

    private Mono<TripSearchResponseV2> processInternal(TripSearchRequest request) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> validateAndSearch(request, correlationId.toString()));
    }

    private Mono<TripSearchResponseV2> validateAndSearch(TripSearchRequest request, String correlationId) {
        if (request.getFrom() == null || request.getTo() == null) {
            return responseBuilder.createErrorResponse(
                    TripPlanningException.PlanningErrorType.MISSING_LOCATION, null);
        }
        if (!isLocationInTurkmenistan(request.getFrom()) || !isLocationInTurkmenistan(request.getTo())) {
            return responseBuilder.createErrorResponse(
                    TripPlanningException.PlanningErrorType.LOCATION_OUT_OF_BOUNDS, null);
        }

        SearchContext context = contextFactory.createFromRequest(request, correlationId);

        return parallelSearchService.searchAllRoutes(context)
                .timeout(config.getTotalSearchTimeout())
                .flatMap(tripPlan -> responseBuilder.createSuccessResponse(tripPlan, context))
                .onErrorResume(error -> handleSearchError(error));
    }

    private boolean isLocationInTurkmenistan(TripSearchRequest.LocationDTO location) {
        return location.getLatitude() >= 35.0 && location.getLatitude() <= 43.0 &&
                location.getLongitude() >= 52.0 && location.getLongitude() <= 67.0;
    }

    private Mono<TripSearchResponseV2> handleSearchError(Throwable error) {
        if (error instanceof TripPlanningException tripEx) {
            log.warn("Trip planning error (v2): {} - {}", tripEx.getPlanningErrorType(), tripEx.getMessage());
            return responseBuilder.createErrorResponseFromException(tripEx);
        }
        log.error("Technical error during trip search (v2): {}", error.getMessage(), error);
        return Mono.error(error);
    }
}
