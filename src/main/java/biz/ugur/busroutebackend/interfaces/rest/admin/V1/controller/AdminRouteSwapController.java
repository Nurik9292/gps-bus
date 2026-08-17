package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.routeswap.ReassignVehicleRequest;
import biz.ugur.busroutebackend.shared.infrastructure.web.BasePaginatedController;
import biz.ugur.busroutebackend.transport.application.dto.routeswap.OperatorReassignmentDTO;
import biz.ugur.busroutebackend.transport.application.dto.routeswap.ReassignVehicleCommand;
import biz.ugur.busroutebackend.transport.application.dto.routeswap.RouteSwapSummaryDTO;
import biz.ugur.busroutebackend.transport.application.dto.routeswap.RouteSwapVerdictDTO;
import biz.ugur.busroutebackend.transport.application.dto.routeswap.VehicleReassignmentDTO;
import biz.ugur.busroutebackend.transport.application.usecase.routeswap.GetOperatorReassignmentsUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.routeswap.GetRouteSwapVerdictsUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.routeswap.VehicleRouteReassignmentUseCase;
import biz.ugur.busroutebackend.transport.infrastructure.monitoring.routeswap.RouteSwapProperties;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    private static final String REASSIGN_DISABLED_CODE = "ROUTE_SWAP_REASSIGN_DISABLED";
    private static final String REASSIGN_DISABLED_MESSAGE =
            "Manual reassignment is disabled: business.route-swap-detector.reassign-enabled=false";

    private final GetRouteSwapVerdictsUseCase getRouteSwapVerdictsUseCase;
    private final VehicleRouteReassignmentUseCase vehicleRouteReassignmentUseCase;
    private final GetOperatorReassignmentsUseCase getOperatorReassignmentsUseCase;
    private final RouteSwapProperties routeSwapProperties;

    public AdminRouteSwapController(GetRouteSwapVerdictsUseCase getRouteSwapVerdictsUseCase,
                                    VehicleRouteReassignmentUseCase vehicleRouteReassignmentUseCase,
                                    GetOperatorReassignmentsUseCase getOperatorReassignmentsUseCase,
                                    RouteSwapProperties routeSwapProperties,
                                    MessageSource messageSource) {
        super(messageSource);
        this.getRouteSwapVerdictsUseCase = getRouteSwapVerdictsUseCase;
        this.vehicleRouteReassignmentUseCase = vehicleRouteReassignmentUseCase;
        this.getOperatorReassignmentsUseCase = getOperatorReassignmentsUseCase;
        this.routeSwapProperties = routeSwapProperties;
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
        return ok(getRouteSwapVerdictsUseCase.summary()
                .map(summary -> summary.withReassignEnabled(routeSwapProperties.isReassignEnabled())));
    }

    @GetMapping("/reassignments")
    public Mono<ResponseEntity<ApiResponse<List<OperatorReassignmentDTO>>>> getReassignments() {
        if (!routeSwapProperties.isReassignEnabled()) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(
                    REASSIGN_DISABLED_CODE, REASSIGN_DISABLED_MESSAGE)));
        }
        return ok(getOperatorReassignmentsUseCase.activeReassignments());
    }

    @PostMapping("/reassign")
    public Mono<ResponseEntity<ApiResponse<VehicleReassignmentDTO>>> reassign(
            @Valid @RequestBody ReassignVehicleRequest request) {
        if (!routeSwapProperties.isReassignEnabled()) {
            return Mono.just(reassignmentDisabled());
        }
        return ok(vehicleRouteReassignmentUseCase.reassign(new ReassignVehicleCommand(
                request.vehicleId(), request.routeNumber(), request.reason())));
    }

    @PostMapping("/vehicles/{vehicleId}/revert")
    public Mono<ResponseEntity<ApiResponse<VehicleReassignmentDTO>>> revert(@PathVariable String vehicleId) {
        if (!routeSwapProperties.isReassignEnabled()) {
            return Mono.just(reassignmentDisabled());
        }
        return ok(vehicleRouteReassignmentUseCase.revert(vehicleId));
    }

    private static ResponseEntity<ApiResponse<VehicleReassignmentDTO>> reassignmentDisabled() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(
                REASSIGN_DISABLED_CODE, REASSIGN_DISABLED_MESSAGE));
    }
}
