package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.admin.application.usecase.analytics.GetHeatmapDataUseCase;
import biz.ugur.busroutebackend.admin.application.usecase.analytics.GetODPairsUseCase;
import biz.ugur.busroutebackend.admin.application.usecase.analytics.GetRoutingAnalyticsOverviewUseCase;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.analytics.HeatmapResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.analytics.ODPairsResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.analytics.RoutingAnalyticsResponse;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_ANALYTICS;

@RestController
@RequestMapping(V1_ADMIN_ANALYTICS + "/routing")
public class AdminRoutingAnalyticsController extends BaseController {

    private final GetRoutingAnalyticsOverviewUseCase getRoutingAnalyticsOverviewUseCase;
    private final GetHeatmapDataUseCase getHeatmapDataUseCase;
    private final GetODPairsUseCase getODPairsUseCase;

    public AdminRoutingAnalyticsController(
            GetRoutingAnalyticsOverviewUseCase getRoutingAnalyticsOverviewUseCase,
            GetHeatmapDataUseCase getHeatmapDataUseCase,
            GetODPairsUseCase getODPairsUseCase,
            MessageSource messageSource) {
        super(messageSource);
        this.getRoutingAnalyticsOverviewUseCase = getRoutingAnalyticsOverviewUseCase;
        this.getHeatmapDataUseCase = getHeatmapDataUseCase;
        this.getODPairsUseCase = getODPairsUseCase;
    }

    @Override
    protected String getControllerName() {
        return AdminRoutingAnalyticsController.class.getSimpleName();
    }

    @GetMapping("/overview")
    public Mono<ResponseEntity<ApiResponse<RoutingAnalyticsResponse>>> getOverview(
            @RequestParam(defaultValue = "week") String period,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to) {
        return ok(getRoutingAnalyticsOverviewUseCase.execute(period, from, to));
    }

    @GetMapping("/heatmap")
    public Mono<ResponseEntity<ApiResponse<HeatmapResponse>>> getHeatmap(
            @RequestParam(defaultValue = "origin") String type) {
        return ok(getHeatmapDataUseCase.execute(type));
    }

    @GetMapping("/od-pairs")
    public Mono<ResponseEntity<ApiResponse<ODPairsResponse>>> getODPairs(
            @RequestParam(defaultValue = "20") int limit) {
        return ok(getODPairsUseCase.execute(limit));
    }
}
