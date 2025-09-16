package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.dto.SearchResult;
import biz.ugur.busroutebackend.routing.application.dto.StopsContext;
import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class ParallelRouteSearchService {

    private final DirectRouteSearchService directRouteSearch;
    private final OneTransferRouteSearchService oneTransferSearch;
    private final TwoTransferRouteSearchService twoTransferSearch;
    private final NearbyStopsService nearbyStopsService;
    private final TripPlanCombiner tripPlanCombiner;
    private final RouteDeduplicationService deduplicationService;
    private StopBasedRouteSearchService stopBasedSearch;

    public ParallelRouteSearchService(DirectRouteSearchService directRouteSearch,
                                      OneTransferRouteSearchService oneTransferSearch,
                                      TwoTransferRouteSearchService twoTransferSearch,
                                      NearbyStopsService nearbyStopsService,
                                      TripPlanCombiner tripPlanCombiner,
                                      RouteDeduplicationService deduplicationService) {
        this.directRouteSearch = directRouteSearch;
        this.oneTransferSearch = oneTransferSearch;
        this.twoTransferSearch = twoTransferSearch;
        this.nearbyStopsService = nearbyStopsService;
        this.tripPlanCombiner = tripPlanCombiner;
        this.deduplicationService = deduplicationService;
    }

    public Mono<TripPlan> searchAllRoutes(SearchContext context) {
        String searchType = stopBasedSearch != null ? "ENHANCED PARALLEL" : "PARALLEL";
        log.info("[{}] Starting {} search for all route types", context.searchId(), searchType);

        return nearbyStopsService.findStopsForBothLocations(context)
                .flatMap(stopsContext -> {
                    if (stopsContext.hasInsufficientStops()) {
                        log.warn("[{}] Insufficient stops found", context.searchId());
                        return Mono.just(TripPlan.empty(context.fromLocation(),
                                context.toLocation(),
                                context.searchCriteria()));
                    }

                    return executeSearchesInParallel(context, stopsContext);
                });
    }

    private Mono<TripPlan> executeSearchesInParallel(SearchContext context, StopsContext stopsContext) {
        // Основные 3 сервиса поиска (всегда выполняются)
        Mono<SearchResult> directSearch = directRouteSearch.search(context, stopsContext);
        Mono<SearchResult> oneTransferSearch = this.oneTransferSearch.search(context, stopsContext);
        Mono<SearchResult> twoTransferSearch = this.twoTransferSearch.search(context, stopsContext);

        if (stopBasedSearch != null) {
            // Расширенный поиск с 4-м сервисом
            return executeEnhancedSearch(context, stopsContext, directSearch, oneTransferSearch, twoTransferSearch);
        } else {
            // Стандартный поиск (3 сервиса)
            return executeStandardSearch(context, directSearch, oneTransferSearch, twoTransferSearch);
        }
    }

    private Mono<TripPlan> executeEnhancedSearch(SearchContext context,
                                                 StopsContext stopsContext,
                                                 Mono<SearchResult> directSearch,
                                                 Mono<SearchResult> oneTransferSearch,
                                                 Mono<SearchResult> twoTransferSearch) {
        // 4-й сервис: поиск через ближайшие остановки
        Mono<SearchResult> stopBasedSearch = this.stopBasedSearch.search(context, stopsContext);

        return Mono.zip(directSearch, oneTransferSearch, twoTransferSearch, stopBasedSearch)
                .map(results -> {
                    List<SearchResult> allResults = List.of(
                            results.getT1(), // direct
                            results.getT2(), // one-transfer
                            results.getT3(), // two-transfer
                            results.getT4()  // stop-based
                    );

                    return combineWithDeduplication(context, allResults);
                })
                .doOnNext(plan -> logEnhancedCombinedResults(context, plan));
    }

    private Mono<TripPlan> executeStandardSearch(SearchContext context,
                                                 Mono<SearchResult> directSearch,
                                                 Mono<SearchResult> oneTransferSearch,
                                                 Mono<SearchResult> twoTransferSearch) {
        return Mono.zip(directSearch, oneTransferSearch, twoTransferSearch)
                .map(results -> tripPlanCombiner.combine(context, results))
                .doOnNext(plan -> logStandardCombinedResults(context, plan));
    }

    private TripPlan combineWithDeduplication(SearchContext context, List<SearchResult> allResults) {
        // Централизованная дедупликация всех результатов
        var uniqueRoutes = deduplicationService.deduplicateRoutes(allResults);

        // Создание TripPlan с уникальными маршрутами
        return tripPlanCombiner.combineWithDeduplication(context, allResults, uniqueRoutes);
    }

    private void logEnhancedCombinedResults(SearchContext context, TripPlan plan) {
        // Подсчет результатов по типам для детального логирования
        long directCount = plan.getDirectOptions().size();
        long oneTransferCount = plan.getTripOptions().stream()
                .filter(option -> option.getTransfersCount() == 1)
                .count();
        long twoTransferCount = plan.getTripOptions().stream()
                .filter(option -> option.getTransfersCount() == 2)
                .count();

        log.info("[{}] Enhanced results: {} direct, {} one-transfer, {} two-transfer (total: {})",
                context.searchId(),
                directCount,
                oneTransferCount,
                twoTransferCount,
                plan.getTripOptions().size());

        // Дополнительная статистика
        if (plan.getTripOptions().size() > (directCount + oneTransferCount + twoTransferCount)) {
            long stopBasedCount = plan.getTripOptions().size() - (directCount + oneTransferCount + twoTransferCount);
            log.info("[{}] Stop-based routes contributed: {} additional unique options",
                    context.searchId(), stopBasedCount);
        }
    }

    private void logStandardCombinedResults(SearchContext context, TripPlan plan) {
        log.info("[{}] Standard results: {} direct, {} total options",
                context.searchId(),
                plan.getDirectOptions().size(),
                plan.getTripOptions().size());
    }
}