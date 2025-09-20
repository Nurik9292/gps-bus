package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.dto.SearchResult;
import biz.ugur.busroutebackend.routing.application.dto.StopsContext;
import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
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
        return nearbyStopsService.findStopsForBothLocations(context)
                .flatMap(stopsContext -> {

                    if (stopsContext.hasInsufficientStops()) {
                        return Mono.just(TripPlan.empty(context.fromLocation(),
                                context.toLocation(),
                                context.searchCriteria()));
                    }

                    return executeSearchesInParallel(context, stopsContext);
                });
    }

    private Mono<TripPlan> executeSearchesInParallel(SearchContext context, StopsContext stopsContext) {
        Mono<SearchResult> directSearch = directRouteSearch.search(context, stopsContext);
        Mono<SearchResult> oneTransferSearch = this.oneTransferSearch.search(context, stopsContext);
        Mono<SearchResult> twoTransferSearch = this.twoTransferSearch.search(context, stopsContext);

        if (stopBasedSearch != null) {
            return executeEnhancedSearch(context, stopsContext, directSearch, oneTransferSearch, twoTransferSearch);
        } else {
            return executeStandardSearch(context, directSearch, oneTransferSearch, twoTransferSearch);
        }
    }

    private Mono<TripPlan> executeEnhancedSearch(SearchContext context,
                                                 StopsContext stopsContext,
                                                 Mono<SearchResult> directSearch,
                                                 Mono<SearchResult> oneTransferSearch,
                                                 Mono<SearchResult> twoTransferSearch) {
        Mono<SearchResult> stopBasedSearch = this.stopBasedSearch.search(context, stopsContext);

        return Mono.zip(directSearch, oneTransferSearch, twoTransferSearch, stopBasedSearch)
                .map(results -> {
                    List<SearchResult> allResults = List.of(
                            results.getT1(),
                            results.getT2(),
                            results.getT3(),
                            results.getT4()
                    );

                    return combineWithDeduplication(context, allResults);
                });

    }

    private Mono<TripPlan> executeStandardSearch(SearchContext context,
                                                 Mono<SearchResult> directSearch,
                                                 Mono<SearchResult> oneTransferSearch,
                                                 Mono<SearchResult> twoTransferSearch) {
        return Mono.zip(directSearch, oneTransferSearch, twoTransferSearch)
                .map(results -> {
//                    tripPlanCombiner.combine(context, results)
                        List<SearchResult> allResults = List.of(
                                results.getT1(),
                                results.getT2(),
                                results.getT3()
                        );

                        return combineWithDeduplication(context, allResults);
                    });
    }

    private TripPlan combineWithDeduplication(SearchContext context, List<SearchResult> allResults) {
        List<TripOption> uniqueRoutes = deduplicationService.deduplicateRoutes(allResults);

        return tripPlanCombiner.combineWithDeduplication(context, allResults, uniqueRoutes);
    }



}