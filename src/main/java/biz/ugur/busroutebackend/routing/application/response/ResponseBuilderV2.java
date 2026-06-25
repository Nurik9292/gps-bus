package biz.ugur.busroutebackend.routing.application.response;

import biz.ugur.busroutebackend.interfaces.rest.routing.V2.response.TripOptionV2DTO;
import biz.ugur.busroutebackend.interfaces.rest.routing.V2.response.TripSearchResponseV2;
import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.domain.exceptions.TripPlanningException;
import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import biz.ugur.busroutebackend.routing.domain.service.ParetoDominanceFilter;
import biz.ugur.busroutebackend.routing.domain.service.TripOptionComparator;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
public class ResponseBuilderV2 {

    private static final int MAX_RETURNED_OPTIONS = 5;

    private final TripOptionDTOConverter dtoConverter;
    private final ParetoDominanceFilter dominanceFilter = new ParetoDominanceFilter();

    public ResponseBuilderV2(TripOptionDTOConverter dtoConverter) {
        this.dtoConverter = dtoConverter;
    }

    public Mono<TripSearchResponseV2> createSuccessResponse(TripPlan tripPlan, SearchContext context) {
        if (tripPlan.getTripOptions().isEmpty()) {
            log.warn("[{}] No routes found - returning error response", context.searchId());
            return Mono.just(TripSearchResponseV2.error(
                    TripPlanningException.PlanningErrorType.NO_ROUTE_FOUND.getDefaultMessage(),
                    TripPlanningException.PlanningErrorType.NO_ROUTE_FOUND.name()));
        }

        TripOptionComparator comparator = new TripOptionComparator(tripPlan.getSearchCriteria());
        List<TripOption> bestOptions = dominanceFilter.filterDominated(tripPlan.getTripOptions()).stream()
                .sorted(comparator)
                .limit(MAX_RETURNED_OPTIONS)
                .toList();

        return Flux.fromIterable(bestOptions)
                .concatMap(option -> dtoConverter.convertToDTO(option)
                        .map(dto -> TripOptionV2DTO.fromV1(dto, option.getInitialWaitingMinutes())))
                .collectList()
                .map(options -> TripSearchResponseV2.success(
                        String.format("Found %d route options", options.size()), options));
    }

    public Mono<TripSearchResponseV2> createErrorResponse(
            TripPlanningException.PlanningErrorType errorType, String customMessage) {
        String message = customMessage != null ? customMessage : errorType.getDefaultMessage();
        return Mono.just(TripSearchResponseV2.error(message, errorType.name()));
    }

    public Mono<TripSearchResponseV2> createErrorResponseFromException(TripPlanningException ex) {
        return createErrorResponse(ex.getPlanningErrorType(), ex.getMessage());
    }
}
