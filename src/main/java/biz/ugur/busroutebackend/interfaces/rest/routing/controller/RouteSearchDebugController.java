package biz.ugur.busroutebackend.interfaces.rest.routing.controller;

import biz.ugur.busroutebackend.interfaces.rest.routing.dto.request.TripSearchRequest;
import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.dto.TripPlanStats;
import biz.ugur.busroutebackend.routing.application.response.TripPlanExtensions;
import biz.ugur.busroutebackend.routing.infrastructure.config.SearchContextFactory;
import biz.ugur.busroutebackend.routing.infrastructure.services.ParallelRouteSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/debug")
@Slf4j
@RequiredArgsConstructor
public class RouteSearchDebugController {

    private final ParallelRouteSearchService parallelSearchService;
    private final SearchContextFactory contextFactory;

    @PostMapping("/search-breakdown")
    public Mono<Map<String, Object>> searchBreakdown(@RequestBody TripSearchRequest request) {
        SearchContext context = contextFactory.createFromRequest(request, "DEBUG");

        return parallelSearchService.searchAllRoutes(context)
                .map(tripPlan -> {
                    Map<String, Object> result = new HashMap<>();

                    TripPlanStats stats = TripPlanExtensions.getStats(tripPlan);
                    result.put("stats", stats);
                    result.put("summary", stats.getSummary());

                    result.put("directOptions", TripPlanExtensions.getDirectOptions(tripPlan));
                    result.put("oneTransferOptions", TripPlanExtensions.getOneTransferOptions(tripPlan));
                    result.put("twoTransferOptions", TripPlanExtensions.getTwoTransferOptions(tripPlan));

                    result.put("bestOptions", TripPlanExtensions.getBestOptionsSorted(tripPlan, 5));

                    return result;
                });
    }

    @PostMapping("/search-timing")
    public Mono<Map<String, Object>> searchTiming(@RequestBody TripSearchRequest request) {
        SearchContext context = contextFactory.createFromRequest(request, "TIMING");
        long startTime = System.currentTimeMillis();

        return parallelSearchService.searchAllRoutes(context)
                .map(tripPlan -> {
                    long totalTime = System.currentTimeMillis() - startTime;

                    Map<String, Object> result = new HashMap<>();
                    result.put("totalTimeMs", totalTime);
                    result.put("optionsFound", tripPlan.getTripOptions().size());
                    result.put("breakdown", Map.of(
                            "direct", TripPlanExtensions.getDirectOptions(tripPlan).size(),
                            "oneTransfer", TripPlanExtensions.getOneTransferOptions(tripPlan).size(),
                            "twoTransfer", TripPlanExtensions.getTwoTransferOptions(tripPlan).size()
                    ));

                    return result;
                });
    }
}