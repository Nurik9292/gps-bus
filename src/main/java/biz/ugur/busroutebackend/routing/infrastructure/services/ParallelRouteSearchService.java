package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.dto.SearchResult;
import biz.ugur.busroutebackend.routing.application.dto.StopsContext;
import biz.ugur.busroutebackend.routing.application.factory.TripPlanFactory;
import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Service
@Slf4j
public class ParallelRouteSearchService {

    private static final double SHORT_WALK_THRESHOLD_METERS = 150.0;

    private final DirectRouteSearchService directRouteSearch;
    private final OneTransferRouteSearchService oneTransferSearch;
    private final TwoTransferRouteSearchService twoTransferSearch;
    private final WalkingOnlyRouteSearchService walkingOnlySearch;
    private final NearbyStopsService nearbyStopsService;
    private final TripPlanCombiner tripPlanCombiner;
    private final RouteDeduplicationService deduplicationService;
    private final DistanceCalculationService distanceService;
    private final TripPlanFactory tripPlanFactory;
    private StopBasedRouteSearchService stopBasedSearch;

    public ParallelRouteSearchService(DirectRouteSearchService directRouteSearch,
                                      OneTransferRouteSearchService oneTransferSearch,
                                      TwoTransferRouteSearchService twoTransferSearch,
                                      WalkingOnlyRouteSearchService walkingOnlySearch,
                                      NearbyStopsService nearbyStopsService,
                                      TripPlanCombiner tripPlanCombiner,
                                      RouteDeduplicationService deduplicationService,
                                      DistanceCalculationService distanceService,
                                      TripPlanFactory tripPlanFactory) {
        this.directRouteSearch = directRouteSearch;
        this.oneTransferSearch = oneTransferSearch;
        this.twoTransferSearch = twoTransferSearch;
        this.walkingOnlySearch = walkingOnlySearch;
        this.nearbyStopsService = nearbyStopsService;
        this.tripPlanCombiner = tripPlanCombiner;
        this.deduplicationService = deduplicationService;
        this.distanceService = distanceService;
        this.tripPlanFactory = tripPlanFactory;
    }

    public Mono<TripPlan> searchAllRoutes(SearchContext context) {
        log.info("[{}] 🚀 Starting route search from [{}, {}] to [{}, {}]",
                context.searchId(),
                context.fromLocation().getLatitudeAsDouble(),
                context.fromLocation().getLongitudeAsDouble(),
                context.toLocation().getLatitudeAsDouble(),
                context.toLocation().getLongitudeAsDouble());

        if (isShortWalk(context)) {
            return buildShortWalkOnlyPlan(context);
        }

        log.info("[{}] >>> Step 1: Finding nearby stops", context.searchId());
        return nearbyStopsService.findStopsForBothLocations(context)
                .timeout(Duration.ofSeconds(5))
                .doOnNext(stopsContext -> log.info("[{}] 📍 Found stops: from={}, to={}",
                        context.searchId(),
                        stopsContext.fromStops().size(),
                        stopsContext.toStops().size()))
                .flatMap(stopsContext -> {

                    if (stopsContext.hasInsufficientStops()) {
                        log.warn("[{}] ⚠️ INSUFFICIENT STOPS - from={}, to={}",
                                context.searchId(),
                                stopsContext.fromStops().size(),
                                stopsContext.toStops().size());
                        return Mono.just(tripPlanFactory.createNew(
                                context.fromLocation(),
                                context.toLocation(),
                                context.searchCriteria()));
                    }

                    return executeSearchesInParallel(context, stopsContext);
                });
    }

    private boolean isShortWalk(SearchContext context) {
        double meters = DistanceCalculationService.haversineDistanceMeters(
                context.fromLocation().getLatitudeAsDouble(),
                context.fromLocation().getLongitudeAsDouble(),
                context.toLocation().getLatitudeAsDouble(),
                context.toLocation().getLongitudeAsDouble());
        return meters <= SHORT_WALK_THRESHOLD_METERS;
    }

    private Mono<TripPlan> buildShortWalkOnlyPlan(SearchContext context) {
        log.info("[{}] Short walk shortcut: endpoints within {} m — returning walking-only",
                context.searchId(), (int) SHORT_WALK_THRESHOLD_METERS);
        return walkingOnlySearch.search(context)
                .map(walkingResult -> tripPlanCombiner.combineWithDeduplication(
                        context,
                        List.of(walkingResult),
                        walkingResult.getOptions()));
    }

    private Mono<TripPlan> executeSearchesInParallel(SearchContext context, StopsContext stopsContext) {
        Mono<SearchResult> directSearch = directRouteSearch.search(context, stopsContext);
        Mono<SearchResult> oneTransferSearch = this.oneTransferSearch.search(context, stopsContext);
        Mono<SearchResult> twoTransferSearch = this.twoTransferSearch.search(context, stopsContext);
        Mono<SearchResult> walkingOnlySearch = this.walkingOnlySearch.search(context)
                .onErrorResume(e -> Mono.just(SearchResult.failed("walking_only", e.getMessage())));

        if (stopBasedSearch != null) {
            return executeEnhancedSearch(context, stopsContext, directSearch, oneTransferSearch, twoTransferSearch);
        } else {
            return executeStandardSearch(context, directSearch, oneTransferSearch, twoTransferSearch, walkingOnlySearch);
        }
    }

    private Mono<TripPlan> executeEnhancedSearch(SearchContext context,
                                                 StopsContext stopsContext,
                                                 Mono<SearchResult> directSearch,
                                                 Mono<SearchResult> oneTransferSearch,
                                                 Mono<SearchResult> twoTransferSearch) {
        Mono<SearchResult> stopBasedSearch = this.stopBasedSearch.search(context, stopsContext);

        return directSearch.flatMap(directResult -> {
            log.info("[{}] 🔍 Direct search completed: {} options",
                    context.searchId(), directResult.getOptionsCount());

            return Mono.zip(oneTransferSearch, twoTransferSearch, stopBasedSearch)
                    .map(transferResults -> {
                        List<SearchResult> allResults = List.of(
                                directResult,
                                transferResults.getT1(),
                                transferResults.getT2(),
                                transferResults.getT3()
                        );
                        return combineWithDeduplication(context, allResults);
                    });
        });
    }

    private Mono<TripPlan> executeStandardSearch(SearchContext context,
                                                 Mono<SearchResult> directSearch,
                                                 Mono<SearchResult> oneTransferSearch,
                                                 Mono<SearchResult> twoTransferSearch,
                                                 Mono<SearchResult> walkingOnlySearch) {
        return directSearch.flatMap(directResult -> {
            log.info("[{}] 🔍 Direct search completed: {} options",
                    context.searchId(), directResult.getOptionsCount());

            return Mono.zip(oneTransferSearch, twoTransferSearch, walkingOnlySearch)
                    .map(results -> {
                        log.info("[{}] 🔍 Search results: direct={}, oneTransfer={}, twoTransfer={}, walkingOnly={}",
                                context.searchId(),
                                directResult.getOptionsCount(),
                                results.getT1().getOptionsCount(),
                                results.getT2().getOptionsCount(),
                                results.getT3().getOptionsCount());

                        List<SearchResult> allResults = List.of(
                                directResult,
                                results.getT1(),
                                results.getT2(),
                                results.getT3()
                        );

                        return combineWithDeduplication(context, allResults);
                    });
        });
    }

    private TripPlan combineWithDeduplication(SearchContext context, List<SearchResult> allResults) {
        List<TripOption> uniqueRoutes = deduplicationService.deduplicateRoutes(allResults);

        return tripPlanCombiner.combineWithDeduplication(context, allResults, uniqueRoutes);
    }



}