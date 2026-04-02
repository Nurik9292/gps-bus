package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.application.builders.DirectRouteOptionBuilder;
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
public class DirectRouteSearchService {

    private final RouteCalculationService routeCalculationService;
    private final DirectRouteOptionBuilder optionBuilder;

    private static final Duration SEARCH_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration OPTION_BUILD_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_RESULTS = 5;
    private static final int BUILD_CONCURRENCY = 4;

    public DirectRouteSearchService(RouteCalculationService routeCalculationService, DirectRouteOptionBuilder optionBuilder) {
        this.routeCalculationService = routeCalculationService;
        this.optionBuilder = optionBuilder;
    }

    public Mono<SearchResult> search(SearchContext context, StopsContext stopsContext) {
        return performDirectSearch(context, stopsContext)
                .timeout(SEARCH_TIMEOUT)
                .doOnNext(result -> log.info("[{}] Direct routes: {} options",
                        context.searchId(), result.getOptionsCount()))
                .onErrorResume(error -> handleSearchError(error, context, "direct"));
    }

    private Mono<SearchResult> performDirectSearch(SearchContext context, StopsContext stopsContext) {
        log.info("[{}] Executing direct route SQL query", context.searchId());
        return routeCalculationService.findDirectRoutes(
                        stopsContext.fromStops(), stopsContext.toStops())
                .filter(this::isRouteViable)
                .collectList()
                // Sort by proximity to fromLocation, then take MAX_RESULTS — BEFORE building options.
                // This prevents createOption (OSRM + ETA calls) from running on all SQL results.
                .map(routes -> sortByProximity(routes, context.fromLocation())
                        .stream().limit(MAX_RESULTS).toList())
                .flatMapMany(Flux::fromIterable)
                .flatMap(route -> optionBuilder.createOption(route, context)
                        .timeout(OPTION_BUILD_TIMEOUT, Mono.empty()), BUILD_CONCURRENCY)
                .filter(Objects::nonNull)
                .collectList()
                .map(options -> SearchResult.successful("direct", options));
    }

    private boolean isRouteViable(RouteCalculationService.DirectRouteResult route) {
        return route.estimatedTravelMinutes() >= 2 && route.estimatedTravelMinutes() <= 120;
    }

    private List<RouteCalculationService.DirectRouteResult> sortByProximity(
            List<RouteCalculationService.DirectRouteResult> routes, Coordinates fromLocation) {
        return routes.stream()
                .sorted(Comparator
                        .comparingDouble((RouteCalculationService.DirectRouteResult r) ->
                                distanceToStop(r.fromStop(), fromLocation))
                        .thenComparingInt(RouteCalculationService.DirectRouteResult::estimatedTravelMinutes))
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
