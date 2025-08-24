package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.dto.SearchResult;
import biz.ugur.busroutebackend.routing.application.dto.StopsContext;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.routing.application.builders.TransferRouteOptionBuilder;
import biz.ugur.busroutebackend.routing.application.builders.DirectRouteOptionBuilder;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
@Slf4j
public class StopBasedRouteSearchService {

    private final RouteCalculationService routeCalculationService;
    private final NearbyStopsService nearbyStopsService;
    private final TransferRouteOptionBuilder transferOptionBuilder;
    private final DirectRouteOptionBuilder directOptionBuilder;

    private static final Duration SEARCH_TIMEOUT = Duration.ofSeconds(18);
    private static final int MAX_RESULTS = 6;
    private static final int MAX_STOP_LAYERS = 3;

    // Transfer distance limits for each layer
    private static final double[] LAYER_TRANSFER_DISTANCES = {0.3, 0.4, 0.5}; // км

    public StopBasedRouteSearchService(RouteCalculationService routeCalculationService,
                                       NearbyStopsService nearbyStopsService,
                                       TransferRouteOptionBuilder transferOptionBuilder,
                                       DirectRouteOptionBuilder directOptionBuilder) {
        this.routeCalculationService = routeCalculationService;
        this.nearbyStopsService = nearbyStopsService;
        this.transferOptionBuilder = transferOptionBuilder;
        this.directOptionBuilder = directOptionBuilder;
    }

    // ✅ ЕДИНСТВЕННАЯ ОТВЕТСТВЕННОСТЬ: поиск маршрутов через ближайшие остановки
    public Mono<SearchResult> search(SearchContext context, StopsContext originalStopsContext) {
        return performStopBasedSearch(context)
                .timeout(SEARCH_TIMEOUT)
                .map(routes -> SearchResult.successful("stop-based", routes))
                .doOnNext(result -> log.info("[{}] Stop-based routes: {} options found",
                        context.searchId(), result.getOptionsCount()))
                .onErrorResume(error -> handleSearchError(error, context));
    }

    private Mono<List<TripOption>> performStopBasedSearch(SearchContext context) {
        return nearbyStopsService.findMultipleNearbyStops(context)
                .flatMap(multiStops -> performLayeredSearch(context, multiStops, 0));
    }

    // ✅ Итеративный поиск по слоям остановок
    private Mono<List<TripOption>> performLayeredSearch(
            SearchContext context,
            NearbyStopsService.MultiLayerStopsContext multiStops,
            int currentLayer) {

        if (currentLayer >= MAX_STOP_LAYERS || currentLayer >= multiStops.getLayerCount()) {
            log.debug("[{}] Reached max layer or no more layers available", context.searchId());
            return Mono.just(Collections.emptyList());
        }

        StopsContext layerStops = multiStops.getLayer(currentLayer);

        if (layerStops.hasInsufficientStops()) {
            log.debug("[{}] Layer {} has insufficient stops, trying next layer",
                    context.searchId(), currentLayer);
            return performLayeredSearch(context, multiStops, currentLayer + 1);
        }

        return searchInLayer(context, layerStops, currentLayer)
                .flatMap(results -> {
                    if (!results.isEmpty()) {
                        double radius = getRadiusForLayer(currentLayer);
                        log.debug("[{}] Found {} routes in layer {} (radius: {}km)",
                                context.searchId(), results.size(), currentLayer, radius);
                        return Mono.just(results);
                    } else {
                        log.debug("[{}] No routes in layer {}, trying next layer",
                                context.searchId(), currentLayer);
                        return performLayeredSearch(context, multiStops, currentLayer + 1);
                    }
                });
    }

    // ✅ Поиск в конкретном слое остановок
    private Mono<List<TripOption>> searchInLayer(SearchContext context, StopsContext stopsContext, int layerIndex) {
        // Попытка найти прямые маршруты
        return findDirectRoutesInLayer(context, stopsContext)
                .flatMap(directRoutes -> {
                    if (!directRoutes.isEmpty()) {
                        log.debug("[{}] Found {} direct routes in layer {}",
                                context.searchId(), directRoutes.size(), layerIndex);
                        return Mono.just(directRoutes);
                    }
                    // Если прямых нет, ищем с одной пересадкой
                    return findOneTransferRoutesInLayer(context, stopsContext, layerIndex);
                })
                .flatMap(oneTransferRoutes -> {
                    if (!oneTransferRoutes.isEmpty()) {
                        log.debug("[{}] Found {} one-transfer routes in layer {}",
                                context.searchId(), oneTransferRoutes.size(), layerIndex);
                        return Mono.just(oneTransferRoutes);
                    }
                    // Если с одной пересадкой нет, ищем с двумя
                    return findTwoTransferRoutesInLayer(context, stopsContext, layerIndex);
                });
    }

    // ✅ Поиск прямых маршрутов в слое
    private Mono<List<TripOption>> findDirectRoutesInLayer(SearchContext context, StopsContext stopsContext) {
        return routeCalculationService.findDirectRoutes(stopsContext.fromStops(), stopsContext.toStops())
                .filter(this::isDirectRouteViable)
                .flatMap(route -> directOptionBuilder.createOption(route, context))
                .filter(Objects::nonNull)
                .take(MAX_RESULTS)
                .collectList()
                .doOnNext(routes -> {
                    if (!routes.isEmpty()) {
                        log.debug("[{}] Direct routes found: {} options", context.searchId(), routes.size());
                    }
                });
    }

    // ✅ Поиск маршрутов с одной пересадкой в слое
    private Mono<List<TripOption>> findOneTransferRoutesInLayer(
            SearchContext context, StopsContext stopsContext, int layerIndex) {

        double transferDistance = getTransferDistanceForLayer(layerIndex);

        return routeCalculationService.findRoutesWithOneTransfer(
                        stopsContext.fromStops(),
                        stopsContext.toStops(),
                        transferDistance)
                .filter(this::isOneTransferRouteViable)
                .flatMap(route -> transferOptionBuilder.createOneTransferOption(route, context))
                .filter(Objects::nonNull)
                .take(MAX_RESULTS)
                .collectList()
                .doOnNext(routes -> {
                    if (!routes.isEmpty()) {
                        log.debug("[{}] One-transfer routes found: {} options (max distance: {}km)",
                                context.searchId(), routes.size(), transferDistance);
                    }
                });
    }

    // ✅ Поиск маршрутов с двумя пересадками в слое
    private Mono<List<TripOption>> findTwoTransferRoutesInLayer(
            SearchContext context, StopsContext stopsContext, int layerIndex) {

        double transferDistance = getTransferDistanceForLayer(layerIndex);

        return routeCalculationService.findRoutesWithTwoTransfers(
                        stopsContext.fromStops(),
                        stopsContext.toStops(),
                        transferDistance)
                .filter(this::isTwoTransferRouteViable)
                .flatMap(route -> transferOptionBuilder.createTwoTransferOption(route, context))
                .filter(Objects::nonNull)
                .take(MAX_RESULTS / 2) // Меньше результатов для двух пересадок
                .collectList()
                .doOnNext(routes -> {
                    if (!routes.isEmpty()) {
                        log.debug("[{}] Two-transfer routes found: {} options (max distance: {}km)",
                                context.searchId(), routes.size(), transferDistance);
                    }
                });
    }

    // ✅ Валидация прямых маршрутов
    private boolean isDirectRouteViable(RouteCalculationService.DirectRouteResult route) {
        return route.estimatedTravelMinutes() >= 2 &&
                route.estimatedTravelMinutes() <= 120 &&
                route.walkingDistanceToStart() <= 800 && // 800м пешком до начальной остановки
                route.walkingDistanceFromEnd() <= 800;   // 800м пешком от конечной остановки
    }

    // ✅ Валидация маршрутов с одной пересадкой
    private boolean isOneTransferRouteViable(RouteCalculationService.TransferRouteResult route) {
        int totalTime = route.firstRouteTravelMinutes() +
                route.transferWaitMinutes() +
                route.secondRouteTravelMinutes();

        return route.firstRouteTravelMinutes() >= 3 &&
                route.secondRouteTravelMinutes() >= 3 &&
                route.transferWaitMinutes() <= 15 &&
                totalTime <= 120 &&
                route.walkingDistanceToStart() <= 800 &&
                route.walkingDistanceFromEnd() <= 800;
    }

    // ✅ Валидация маршрутов с двумя пересадками
    private boolean isTwoTransferRouteViable(RouteCalculationService.TwoTransferRouteResult route) {
        int totalTime = route.firstRouteTravelMinutes() +
                route.secondRouteTravelMinutes() +
                route.thirdRouteTravelMinutes() +
                route.firstTransferWaitMinutes() +
                route.secondTransferWaitMinutes();

        return route.firstRouteTravelMinutes() >= 3 &&
                route.secondRouteTravelMinutes() >= 3 &&
                route.thirdRouteTravelMinutes() >= 3 &&
                route.firstTransferWaitMinutes() <= 12 &&
                route.secondTransferWaitMinutes() <= 12 &&
                totalTime <= 150 &&
                route.walkingDistanceToStart() <= 800 &&
                route.walkingDistanceFromEnd() <= 800;
    }

    // ✅ Получение радиуса для слоя (для логирования)
    private double getRadiusForLayer(int layerIndex) {
        double[] radiuses = {0.3, 0.6, 1.0};
        return layerIndex < radiuses.length ? radiuses[layerIndex] : 1.0;
    }

    // ✅ Получение максимальной дистанции пересадки для слоя
    private double getTransferDistanceForLayer(int layerIndex) {
        return layerIndex < LAYER_TRANSFER_DISTANCES.length ?
                LAYER_TRANSFER_DISTANCES[layerIndex] : 0.5;
    }

    // ✅ Обработка ошибок
    private Mono<SearchResult> handleSearchError(Throwable error, SearchContext context) {
        log.warn("[{}] Stop-based search failed: {}", context.searchId(), error.getMessage());
        return Mono.just(SearchResult.failed("stop-based", error.getMessage()));
    }
}