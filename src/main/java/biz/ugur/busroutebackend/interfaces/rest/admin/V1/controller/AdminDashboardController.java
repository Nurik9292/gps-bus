package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.admin.application.usecase.dashboard.GetDashboardStatisticsUseCase;
import biz.ugur.busroutebackend.advertising.application.dto.AdvertisingDashboardOverview;
import biz.ugur.busroutebackend.advertising.application.usecase.admin.GetAdvertisingDashboardOverviewUseCase;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.dashboard.DashboardStatisticsResponse;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_DASHBOARD;

@RestController
@RequestMapping(V1_ADMIN_DASHBOARD)
public class AdminDashboardController extends BaseController {

    private final GetDashboardStatisticsUseCase getDashboardStatisticsUseCase;
    private final GetAdvertisingDashboardOverviewUseCase getAdvertisingDashboardOverviewUseCase;

    public AdminDashboardController(
            GetDashboardStatisticsUseCase getDashboardStatisticsUseCase,
            GetAdvertisingDashboardOverviewUseCase getAdvertisingDashboardOverviewUseCase,
            MessageSource messageSource) {
        super(messageSource);
        this.getDashboardStatisticsUseCase = getDashboardStatisticsUseCase;
        this.getAdvertisingDashboardOverviewUseCase = getAdvertisingDashboardOverviewUseCase;
    }

    @Override
    protected String getControllerName() {
        return AdminDashboardController.class.getSimpleName();
    }

    @GetMapping("/stats")
    public Mono<ResponseEntity<ApiResponse<DashboardStatisticsResponse>>> getStatistics() {
        return ok(getDashboardStatisticsUseCase.execute()
                .map(DashboardStatisticsResponse::fromResult));
    }

    @GetMapping("/advertising-overview")
    public Mono<ResponseEntity<ApiResponse<AdvertisingDashboardOverview>>> getAdvertisingOverview() {
        return ok(getAdvertisingDashboardOverviewUseCase.execute(null));
    }
}
