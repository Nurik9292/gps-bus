package biz.ugur.busroutebackend.routing.application.response;

import biz.ugur.busroutebackend.interfaces.rest.routing.V1.response.TripSearchResponse;
import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.dto.TripOptionDTO;
import biz.ugur.busroutebackend.routing.domain.exceptions.TripPlanningException;
import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import biz.ugur.busroutebackend.routing.domain.service.TripOptionComparator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component("customResponseBuilder")
public class ResponseBuilder {

    private final TripOptionDTOConverter dtoConverter;

    public ResponseBuilder(TripOptionDTOConverter dtoConverter) {
        this.dtoConverter = dtoConverter;
    }

    public Mono<TripSearchResponse> createSuccessResponse(TripPlan tripPlan, SearchContext context) {
        if (tripPlan.getTripOptions().isEmpty()) {
            log.warn("[{}] No routes found - returning error response", context.searchId());
            return Mono.just(new TripSearchResponse(
                    "error",
                    TripPlanningException.PlanningErrorType.NO_ROUTE_FOUND.getDefaultMessage(),
                    TripPlanningException.PlanningErrorType.NO_ROUTE_FOUND.name()
            ));
        }

        return selectAndConvertBestOptions(tripPlan)
                .map(options -> {
                    String message = createSuccessMessage(tripPlan);
                    TripSearchResponse response = new TripSearchResponse("success", message, options);
                    response.setSearchTime(LocalDateTime.now());
                    logResponseCreated(context, tripPlan, options.size());
                    return response;
                });
    }

    public Mono<TripSearchResponse> createErrorResponse(String message) {
        return Mono.just(new TripSearchResponse("error", message, List.of()));
    }

    public Mono<TripSearchResponse> createErrorResponse(
            TripPlanningException.PlanningErrorType errorType,
            String customMessage) {

        String message = customMessage != null ? customMessage : errorType.getDefaultMessage();
        return Mono.just(new TripSearchResponse("error", message, errorType.name()));
    }

    public Mono<TripSearchResponse> createErrorResponseFromException(TripPlanningException ex) {
        return createErrorResponse(ex.getPlanningErrorType(), ex.getMessage());
    }

    private Mono<List<TripOptionDTO>> selectAndConvertBestOptions(TripPlan tripPlan) {
        TripOptionComparator comparator = new TripOptionComparator(tripPlan.getSearchCriteria());
        return Flux.fromIterable(tripPlan.getBestOptions(5, comparator))
                .concatMap(dtoConverter::convertToDTO)
                .collectList();
    }

    private String createSuccessMessage(TripPlan tripPlan) {
        int totalOptions = tripPlan.getTripOptions().size();
        int directOptions = tripPlan.getDirectOptions().size();
        int transferOptions = tripPlan.getTransferOptions().size();

        return String.format("Found %d route options (%d direct, %d with transfers)",
                totalOptions, directOptions, transferOptions);
    }

    private void logResponseCreated(SearchContext context, TripPlan tripPlan, int returnedOptions) {
        long duration = System.currentTimeMillis() - context.startTime();

        log.info("[{}] Response created in {}ms: {} total options, {} returned to user",
                context.searchId(), duration, tripPlan.getTripOptions().size(), returnedOptions);

        if (tripPlan.getTripOptions().isEmpty()) {
            logEmptyResultDiagnostics(context, tripPlan);
        }
    }

    private void logEmptyResultDiagnostics(SearchContext context, TripPlan tripPlan) {
        log.warn("[{}] Empty result diagnostics:", context.searchId());
        log.warn("  - Origin: {}", tripPlan.getOriginLocation());
        log.warn("  - Destination: {}", tripPlan.getDestinationLocation());
        log.warn("  - Max transfers: {}", tripPlan.getSearchCriteria().getMaxTransfers());
        log.warn("  - Max walking distance: {} meters", tripPlan.getSearchCriteria().getMaxWalkingDistanceMeters());
    }
}