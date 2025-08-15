package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.dto.SearchResult;
import biz.ugur.busroutebackend.routing.application.dto.StopsContext;
import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class ParallelRouteSearchService {

    private final DirectRouteSearchService directRouteSearch;
    private final OneTransferRouteSearchService oneTransferSearch;
    private final TwoTransferRouteSearchService twoTransferSearch;
    private final NearbyStopsService nearbyStopsService;
    private final TripPlanCombiner tripPlanCombiner;

    public ParallelRouteSearchService(DirectRouteSearchService directRouteSearch,
                                      OneTransferRouteSearchService oneTransferSearch,
                                      TwoTransferRouteSearchService twoTransferSearch,
                                      NearbyStopsService nearbyStopsService,
                                      TripPlanCombiner tripPlanCombiner) {
        this.directRouteSearch = directRouteSearch;
        this.oneTransferSearch = oneTransferSearch;
        this.twoTransferSearch = twoTransferSearch;
        this.nearbyStopsService = nearbyStopsService;
        this.tripPlanCombiner = tripPlanCombiner;
    }

    public Mono<TripPlan> searchAllRoutes(SearchContext context) {
        log.info("[{}] Starting PARALLEL search for all route types", context.searchId());

        return nearbyStopsService.findStopsForBothLocations(context)
                .flatMap(stopsContext -> {
                    if (stopsContext.hasInsufficientStops()) {
                        log.warn("[{}] Insufficient stops found", context.searchId());
                        return Mono.just(TripPlan.empty(context.fromLocation(), context.toLocation(), context.searchCriteria()));
                    }

                    return searchAllTypesInParallel(context, stopsContext);
                });
    }

    private Mono<TripPlan> searchAllTypesInParallel(SearchContext context, StopsContext stopsContext) {
        Mono<SearchResult> directSearch = directRouteSearch.search(context, stopsContext);
        Mono<SearchResult> oneTransferSearch = this.oneTransferSearch.search(context, stopsContext);
        Mono<SearchResult> twoTransferSearch = this.twoTransferSearch.search(context, stopsContext);

        return Mono.zip(directSearch, oneTransferSearch, twoTransferSearch)
                .map(results -> tripPlanCombiner.combine(context, results))
                .doOnNext(plan -> logCombinedResults(context, plan));
    }

    private void logCombinedResults(SearchContext context, TripPlan plan) {
        log.info("[{}] Combined results: {} direct (total: {})",
                context.searchId(),
                plan.getDirectOptions().size(),
                plan.getTripOptions().size());
    }
}

