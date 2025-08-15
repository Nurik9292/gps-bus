package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.application.builders.DirectRouteOptionBuilder;
import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.dto.SearchResult;
import biz.ugur.busroutebackend.routing.application.dto.StopsContext;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;

@Service
@Slf4j
public class DirectRouteSearchService {

    private final RouteCalculationService routeCalculationService;
    private final DirectRouteOptionBuilder optionBuilder;

    private static final Duration SEARCH_TIMEOUT = Duration.ofSeconds(8);
    private static final int MAX_RESULTS = 5;

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
        return routeCalculationService.findDirectRoutes(
                        stopsContext.fromStops(), stopsContext.toStops())
                .filter(this::isRouteViable)
                .flatMap(route -> optionBuilder.createOption(route, context))
                .filter(Objects::nonNull)
                .take(MAX_RESULTS)
                .collectList()
                .map(options -> SearchResult.successful("direct", options));
    }

    private boolean isRouteViable(RouteCalculationService.DirectRouteResult route) {
        return route.estimatedTravelMinutes() >= 2 && route.estimatedTravelMinutes() <= 120;
    }

    private Mono<SearchResult> handleSearchError(Throwable error, SearchContext context, String type) {
        log.warn("[{}] {} routes failed: {}", context.searchId(), type, error.getMessage());
        return Mono.just(SearchResult.failed(type, error.getMessage()));
    }
}