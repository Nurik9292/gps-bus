package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.application.builders.TransferRouteOptionBuilder;
import biz.ugur.busroutebackend.routing.application.builders.TransferRouteValidator;
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
public class OneTransferRouteSearchService {

    private final RouteCalculationService routeCalculationService;
    private final TransferRouteOptionBuilder optionBuilder;
    private final TransferRouteValidator validator;

    private static final Duration SEARCH_TIMEOUT = Duration.ofSeconds(12);
    private static final int MAX_RESULTS = 8;
    private static final double MAX_TRANSFER_DISTANCE_KM = 0.5;

    public OneTransferRouteSearchService(RouteCalculationService routeCalculationService,
                                         TransferRouteOptionBuilder optionBuilder,
                                         TransferRouteValidator validator) {
        this.routeCalculationService = routeCalculationService;
        this.optionBuilder = optionBuilder;
        this.validator = validator;
    }

    public Mono<SearchResult> search(SearchContext context, StopsContext stopsContext) {
        return performOneTransferSearch(context, stopsContext)
                .timeout(SEARCH_TIMEOUT)
                .doOnNext(result -> log.info("[{}] One-transfer routes: {} options",
                        context.searchId(), result.getOptionsCount()))
                .onErrorResume(error -> handleSearchError(error, context, "one-transfer"));
    }

    private Mono<SearchResult> performOneTransferSearch(SearchContext context, StopsContext stopsContext) {
        return routeCalculationService.findRoutesWithOneTransfer(
                        stopsContext.fromStops(), stopsContext.toStops(), MAX_TRANSFER_DISTANCE_KM)
                .filter(validator::isOneTransferRouteViable)
                .flatMap(route -> optionBuilder.createOneTransferOption(route, context))
                .filter(Objects::nonNull)
                .take(MAX_RESULTS)
                .collectList()
                .map(options -> SearchResult.successful("one-transfer", options));
    }

    private Mono<SearchResult> handleSearchError(Throwable error, SearchContext context, String type) {
        log.warn("[{}] {} routes failed: {}", context.searchId(), type, error.getMessage());
        return Mono.just(SearchResult.failed(type, error.getMessage()));
    }
}