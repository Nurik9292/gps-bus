package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.admin.application.usecase.dashboard.GetDashboardStatisticsUseCase;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.dashboard.DashboardStatisticsResponse;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_DASHBOARD;

@RestController
@RequestMapping(V1_ADMIN_DASHBOARD)
@CrossOrigin("*")
public class AdminDashboardController extends BaseController {

    private final GetDashboardStatisticsUseCase getDashboardStatisticsUseCase;

    public AdminDashboardController(
            GetDashboardStatisticsUseCase getDashboardStatisticsUseCase,
            MessageSource messageSource) {
        super(messageSource);
        this.getDashboardStatisticsUseCase = getDashboardStatisticsUseCase;
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
}
