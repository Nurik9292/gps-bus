package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.controller;

import biz.ugur.busroutebackend.client.application.usecase.dashboard.GetMobileDashboardStatisticsUseCase;
import biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response.MobileDashboardStatisticsResponse;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_MOBILE_DASHBOARD;

@RestController
@RequestMapping(V1_MOBILE_DASHBOARD)
public class MobileDashboardApiController extends BaseController {

    private final GetMobileDashboardStatisticsUseCase getMobileDashboardStatisticsUseCase;

    public MobileDashboardApiController(
            GetMobileDashboardStatisticsUseCase getMobileDashboardStatisticsUseCase,
            MessageSource messageSource) {
        super(messageSource);
        this.getMobileDashboardStatisticsUseCase = getMobileDashboardStatisticsUseCase;
    }

    @Override
    protected String getControllerName() {
        return MobileDashboardApiController.class.getSimpleName();
    }

    @GetMapping("/stats")
    public Mono<ResponseEntity<ApiResponse<MobileDashboardStatisticsResponse>>> getStatistics() {
        return ok(getMobileDashboardStatisticsUseCase.execute()
                .map(MobileDashboardStatisticsResponse::fromResult));
    }
}
