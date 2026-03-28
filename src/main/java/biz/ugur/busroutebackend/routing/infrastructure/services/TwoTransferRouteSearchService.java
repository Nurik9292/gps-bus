package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.application.builders.TransferRouteOptionBuilder;
import biz.ugur.busroutebackend.routing.application.builders.TransferRouteValidator;
import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.dto.SearchResult;
import biz.ugur.busroutebackend.routing.application.dto.StopsContext;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class TwoTransferRouteSearchService {

    private final RouteCalculationService routeCalculationService;
    private final TransferRouteOptionBuilder optionBuilder;
    private final TransferRouteValidator validator;

    private static final Duration SEARCH_TIMEOUT = Duration.ofSeconds(25);
    private static final Duration OPTION_BUILD_TIMEOUT = Duration.ofSeconds(6);
    private static final int MAX_RESULTS = 4;
    private static final int BUILD_CONCURRENCY = 3;
    private static final double MAX_TRANSFER_DISTANCE_KM = 0.3;

    public TwoTransferRouteSearchService(
            RouteCalculationService routeCalculationService,
            TransferRouteOptionBuilder optionBuilder,
            TransferRouteValidator validator) {
        this.routeCalculationService = routeCalculationService;
        this.optionBuilder = optionBuilder;
        this.validator = validator;
    }

    public Mono<SearchResult> search(SearchContext context, StopsContext stopsContext) {
        return performTwoTransferSearch(context, stopsContext)
                .timeout(SEARCH_TIMEOUT)
                .doOnNext(result -> log.info("[{}] Two-transfer routes: {} options",
                        context.searchId(), result.getOptionsCount()))
                .onErrorResume(error -> handleSearchError(error, context, "two-transfer"));
    }

    private Mono<SearchResult> performTwoTransferSearch(SearchContext context, StopsContext stopsContext) {
        return routeCalculationService.findRoutesWithTwoTransfers(
                        stopsContext.fromStops(), stopsContext.toStops(), MAX_TRANSFER_DISTANCE_KM)
                .filter(validator::isTwoTransferRouteViable)
                .collectList()
                // Sort by proximity to fromLocation, then take MAX_RESULTS — BEFORE building options.
                // This prevents createOption (OSRM + ETA calls) from running on all SQL results.
                .map(routes -> sortByProximity(routes, context.fromLocation())
                        .stream().limit(MAX_RESULTS).toList())
                .flatMapMany(Flux::fromIterable)
                .flatMap(route -> optionBuilder.createTwoTransferOption(route, context)
                        .timeout(OPTION_BUILD_TIMEOUT, Mono.empty()), BUILD_CONCURRENCY)
                .filter(Objects::nonNull)
                .collectList()
                .map(options -> SearchResult.successful("two-transfer", options));
    }

    private List<RouteCalculationService.TwoTransferRouteResult> sortByProximity(
            List<RouteCalculationService.TwoTransferRouteResult> routes, Coordinates fromLocation) {
        return routes.stream()
                .sorted(Comparator
                        .comparingDouble((RouteCalculationService.TwoTransferRouteResult r) ->
                                distanceToStop(r.fromStop(), fromLocation))
                        .thenComparingInt(r -> r.firstRouteTravelMinutes()
                                + r.secondRouteTravelMinutes() + r.thirdRouteTravelMinutes()))
                .toList();
    }

    private double distanceToStop(BusStop stop, Coordinates fromLocation) {
        return DistanceCalculationService.haversineDistanceMeters(
                fromLocation.getLatitudeAsDouble(), fromLocation.getLongitudeAsDouble(),
                stop.getLatitude().doubleValue(), stop.getLongitude().doubleValue());
    }

    private Mono<SearchResult> handleSearchError(Throwable error, SearchContext context, String type) {
        log.warn("[{}] {} routes failed: {}", context.searchId(), type, error.getMessage());
        return Mono.just(SearchResult.failed(type, error.getMessage()));
    }
}
