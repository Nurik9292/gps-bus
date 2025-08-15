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
public class TwoTransferRouteSearchService {

    private final RouteCalculationService routeCalculationService;
    private final TransferRouteOptionBuilder optionBuilder;
    private final TransferRouteValidator validator;

    private static final Duration SEARCH_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_RESULTS = 4;
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
                .flatMap(route -> optionBuilder.createTwoTransferOption(route, context))
                .filter(Objects::nonNull)
                .take(MAX_RESULTS)
                .collectList()
                .map(options -> SearchResult.successful("two-transfer", options));
    }

    private Mono<SearchResult> handleSearchError(Throwable error, SearchContext context, String type) {
        log.warn("[{}] {} routes failed: {}", context.searchId(), type, error.getMessage());
        return Mono.just(SearchResult.failed(type, error.getMessage()));
    }
}
