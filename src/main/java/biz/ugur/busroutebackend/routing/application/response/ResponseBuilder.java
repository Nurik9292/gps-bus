package biz.ugur.busroutebackend.routing.application.response;

import biz.ugur.busroutebackend.interfaces.rest.routing.V1.response.TripSearchResponse;
import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.dto.TripOptionDTO;
import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component("customResponseBuilder")
public class ResponseBuilder {

    private final TripOptionDTOConverter dtoConverter;

    public ResponseBuilder(TripOptionDTOConverter dtoConverter) {
        this.dtoConverter = dtoConverter;
    }

    public Mono<TripSearchResponse> createSuccessResponse(TripPlan tripPlan, SearchContext context) {
        return Mono.fromCallable(() -> {
            List<TripOptionDTO> options = selectAndConvertBestOptions(tripPlan);
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

    private List<TripOptionDTO> selectAndConvertBestOptions(TripPlan tripPlan) {
        return tripPlan.getBestOptions(5)
                .stream()
                .map(dtoConverter::convertToDTO)
                .collect(Collectors.toList());
    }

    private String createSuccessMessage(TripPlan tripPlan) {
        int totalOptions = tripPlan.getTripOptions().size();
        int directOptions = tripPlan.getDirectOptions().size();
        int transferOptions = tripPlan.getTransferOptions().size();

        if (totalOptions == 0) {
            return "No routes found between these locations";
        }

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
        log.warn("  - Walkable: {}", tripPlan.isWalkable());
        log.warn("  - Max transfers: {}", tripPlan.getSearchCriteria().getMaxTransfers());
    }
}