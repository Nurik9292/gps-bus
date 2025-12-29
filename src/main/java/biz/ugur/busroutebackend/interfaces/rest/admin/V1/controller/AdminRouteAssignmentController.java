package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.assignment.RouteAssignmentCreateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.assignment.RouteAssignmentListResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.assignment.RouteAssignmentResponse;
import biz.ugur.busroutebackend.shared.application.SecurityContextService;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import biz.ugur.busroutebackend.transport.application.usecase.assignment.CreateRouteAssignmentUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.assignment.DeleteRouteAssignmentUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.assignment.GetRouteAssignmentByIdUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.assignment.GetRouteAssignmentsByVehicleUseCase;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_ROUTE_ASSIGNMENTS;

@Slf4j
@RestController
@RequestMapping(V1_ADMIN_ROUTE_ASSIGNMENTS)
@CrossOrigin(origins = "*")
public class AdminRouteAssignmentController extends BaseController {

    private final CreateRouteAssignmentUseCase createUseCase;
    private final GetRouteAssignmentByIdUseCase getByIdUseCase;
    private final GetRouteAssignmentsByVehicleUseCase getByVehicleUseCase;
    private final DeleteRouteAssignmentUseCase deleteUseCase;
    private final SecurityContextService securityContextService;

    public AdminRouteAssignmentController(
            CreateRouteAssignmentUseCase createUseCase,
            GetRouteAssignmentByIdUseCase getByIdUseCase,
            GetRouteAssignmentsByVehicleUseCase getByVehicleUseCase,
            DeleteRouteAssignmentUseCase deleteUseCase,
            SecurityContextService securityContextService,
            MessageSource messageSource) {
        super(messageSource);
        this.createUseCase = createUseCase;
        this.getByIdUseCase = getByIdUseCase;
        this.getByVehicleUseCase = getByVehicleUseCase;
        this.deleteUseCase = deleteUseCase;
        this.securityContextService = securityContextService;
    }

    @Override
    protected String getControllerName() {
        return AdminRouteAssignmentController.class.getSimpleName();
    }

    @PostMapping
    public Mono<ResponseEntity<ApiResponse<RouteAssignmentResponse>>> createAssignment(
            @Valid @RequestBody RouteAssignmentCreateRequest request) {
        log.info("Creating route assignment: vehicle={}, route={}, date={}, shift={}",
                request.vehicleId(), request.routeId(), request.effectiveDate(), request.shiftType());

        return created(Mono.just(request.toCommand())
                .as(createUseCase::execute)
                .map(RouteAssignmentResponse::fromData));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<RouteAssignmentResponse>>> getAssignmentById(
            @PathVariable String id) {
        log.debug("Getting route assignment by ID: {}", id);

        return ok(Mono.just(id)
                .as(getByIdUseCase::execute)
                .map(RouteAssignmentResponse::fromData));
    }

    @GetMapping("/vehicle/{vehicleId}")
    public Mono<ResponseEntity<ApiResponse<RouteAssignmentListResponse>>> getAssignmentsByVehicle(
            @PathVariable String vehicleId) {
        log.debug("Getting route assignments for vehicle: {}", vehicleId);

        return ok(Mono.just(vehicleId)
                .as(getByVehicleUseCase::execute)
                .map(RouteAssignmentListResponse::fromData));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteAssignment(@PathVariable String id) {
        log.info("Deleting route assignment: {}", id);

        return Mono.just(id)
                .as(deleteUseCase::execute)
                .then(noContent());
    }
}
