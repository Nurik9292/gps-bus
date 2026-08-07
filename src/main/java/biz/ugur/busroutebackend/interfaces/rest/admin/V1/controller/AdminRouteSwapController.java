package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.shared.infrastructure.web.BasePaginatedController;
import biz.ugur.busroutebackend.transport.application.dto.routeswap.RouteSwapSummaryDTO;
import biz.ugur.busroutebackend.transport.application.dto.routeswap.RouteSwapVerdictDTO;
import biz.ugur.busroutebackend.transport.application.usecase.routeswap.GetRouteSwapVerdictsUseCase;
import org.springframework.context.MessageSource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_ROUTE_SWAP;

@RestController
@RequestMapping(V1_ADMIN_ROUTE_SWAP)
public class AdminRouteSwapController extends BasePaginatedController {

    private final GetRouteSwapVerdictsUseCase getRouteSwapVerdictsUseCase;

    public AdminRouteSwapController(GetRouteSwapVerdictsUseCase getRouteSwapVerdictsUseCase,
                                    MessageSource messageSource) {
        super(messageSource);
        this.getRouteSwapVerdictsUseCase = getRouteSwapVerdictsUseCase;
    }

    @Override
    protected String getControllerName() {
        return "AdminRouteSwapController";
    }

    @GetMapping("/verdicts")
    public Mono<ResponseEntity<ApiResponse<List<RouteSwapVerdictDTO>>>> getVerdicts(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String verdict,
            @RequestParam(required = false) Integer limit) {
        return ok(getRouteSwapVerdictsUseCase.list(fromDate, toDate, verdict, limit));
    }

    @GetMapping("/summary")
    public Mono<ResponseEntity<ApiResponse<RouteSwapSummaryDTO>>> getSummary() {
        return ok(getRouteSwapVerdictsUseCase.summary());
    }
}
