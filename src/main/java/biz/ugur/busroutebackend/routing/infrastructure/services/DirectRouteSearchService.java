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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
public class DirectRouteSearchService {

    private final RouteCalculationService routeCalculationService;
    private final DirectRouteOptionBuilder optionBuilder;

    private static final Duration SEARCH_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration OPTION_BUILD_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_RESULTS = 5;
    private static final int BUILD_CONCURRENCY = 2;
    private static final double WALK_SPEED_METERS_PER_MINUTE = 83.33;

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
                .map(routes -> dedupeByRouteNumberAndLimit(
                        sortByTotalCost(routes, context.fromLocation(), context.toLocation())))
                .flatMapMany(Flux::fromIterable)
                .flatMap(route -> optionBuilder.createOption(route, context)
                        .timeout(OPTION_BUILD_TIMEOUT, Mono.empty()), BUILD_CONCURRENCY)
                .filter(Objects::nonNull)
                .collectList()
                .map(options -> SearchResult.successful("direct", options));
    }

    static List<RouteCalculationService.DirectRouteResult> sortByTotalCost(
            List<RouteCalculationService.DirectRouteResult> routes,
            Coordinates fromLocation, Coordinates toLocation) {
        return routes.stream()
                .sorted(Comparator.comparingDouble(route ->
                        estimatedTotalMinutes(route, fromLocation, toLocation)))
                .toList();
    }

    private static double estimatedTotalMinutes(RouteCalculationService.DirectRouteResult route,
                                                Coordinates fromLocation, Coordinates toLocation) {
        double boardingWalkMinutes = walkMinutes(fromLocation, route.fromStop());
        double egressWalkMinutes = walkMinutes(toLocation, route.toStop());
        return boardingWalkMinutes + route.estimatedTravelMinutes() + egressWalkMinutes;
    }

    private static double walkMinutes(Coordinates location, BusStop stop) {
        double meters = DistanceCalculationService.haversineDistanceMeters(
                location.getLatitudeAsDouble(), location.getLongitudeAsDouble(),
                stop.getLatitude().doubleValue(), stop.getLongitude().doubleValue());
        return meters / WALK_SPEED_METERS_PER_MINUTE;
    }

    static List<RouteCalculationService.DirectRouteResult> dedupeByRouteNumberAndLimit(
            List<RouteCalculationService.DirectRouteResult> sortedRoutes) {
        Map<String, RouteCalculationService.DirectRouteResult> bestPerRouteNumber = new LinkedHashMap<>();
        for (RouteCalculationService.DirectRouteResult route : sortedRoutes) {
            bestPerRouteNumber.putIfAbsent(route.route().getRouteNumber(), route);
        }
        return bestPerRouteNumber.values().stream()
                .limit(MAX_RESULTS)
                .toList();
    }

    private boolean isRouteViable(RouteCalculationService.DirectRouteResult route) {
        return route.estimatedTravelMinutes() >= 4 && route.estimatedTravelMinutes() <= 120;
    }

    private Mono<SearchResult> handleSearchError(Throwable error, SearchContext context, String type) {
        log.warn("[{}] {} routes failed: {}", context.searchId(), type, error.getMessage());
        return Mono.just(SearchResult.failed(type, error.getMessage()));
    }
}
