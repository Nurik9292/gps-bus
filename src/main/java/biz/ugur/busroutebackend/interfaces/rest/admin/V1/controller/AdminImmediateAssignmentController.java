package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.immediate.ImmediateAssignmentRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.immediate.ImmediateAssignmentResponse;
import biz.ugur.busroutebackend.shared.application.SecurityContextService;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import biz.ugur.busroutebackend.transport.application.usecase.immediate.ClearImmediateAssignmentUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.immediate.CreateImmediateAssignmentUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.immediate.GetImmediateAssignmentUseCase;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_VEHICLES;

/**
 * Controller for managing immediate route assignments.
 *
 * Immediate assignments have the highest priority and override
 * both shift assignments and GPS-detected routes.
 */
@Slf4j
@RestController
@RequestMapping(V1_ADMIN_VEHICLES)
@CrossOrigin(origins = "*")
public class AdminImmediateAssignmentController extends BaseController {

    private final CreateImmediateAssignmentUseCase createImmediateAssignmentUseCase;
    private final GetImmediateAssignmentUseCase getImmediateAssignmentUseCase;
    private final ClearImmediateAssignmentUseCase clearImmediateAssignmentUseCase;
    private final SecurityContextService securityContextService;

    public AdminImmediateAssignmentController(
            CreateImmediateAssignmentUseCase createImmediateAssignmentUseCase,
            GetImmediateAssignmentUseCase getImmediateAssignmentUseCase,
            ClearImmediateAssignmentUseCase clearImmediateAssignmentUseCase,
            SecurityContextService securityContextService,
            MessageSource messageSource) {
        super(messageSource);
        this.createImmediateAssignmentUseCase = createImmediateAssignmentUseCase;
        this.getImmediateAssignmentUseCase = getImmediateAssignmentUseCase;
        this.clearImmediateAssignmentUseCase = clearImmediateAssignmentUseCase;
        this.securityContextService = securityContextService;
    }

    @Override
    protected String getControllerName() {
        return AdminImmediateAssignmentController.class.getSimpleName();
    }

    /**
     * Create immediate route assignment for a vehicle.
     *
     * This immediately assigns the vehicle to the specified route,
     * overriding any shift assignments or GPS-detected routes.
     */
    @PostMapping("/{vehicleId:[a-f0-9\\-]{36}}/assign-route-now")
    public Mono<ResponseEntity<ApiResponse<ImmediateAssignmentResponse>>> assignRouteNow(
            @PathVariable String vehicleId,
            @Valid @RequestBody ImmediateAssignmentRequest request) {

        return securityContextService.getCurrentUsername()
                .defaultIfEmpty("admin")
                .flatMap(username -> {
                    var command = request.toCommand(vehicleId, username);
                    return createImmediateAssignmentUseCase.execute(command);
                })
                .map(ImmediateAssignmentResponse::fromData)
                .flatMap(response -> created(Mono.just(response)));
    }

    /**
     * Get current immediate assignment for a vehicle.
     *
     * Returns the active immediate assignment if one exists and is not expired.
     */
    @GetMapping("/{vehicleId:[a-f0-9\\-]{36}}/immediate-assignment")
    public Mono<ResponseEntity<ApiResponse<ImmediateAssignmentResponse>>> getImmediateAssignment(
            @PathVariable String vehicleId) {

        return okOrNotFound(
                getImmediateAssignmentUseCase.execute(vehicleId)
                        .map(ImmediateAssignmentResponse::fromData)
        );
    }

    /**
     * Clear immediate assignment for a vehicle.
     *
     * This removes the immediate assignment and reverts the vehicle
     * to the next priority level (shift assignment or GPS detection).
     */
    @DeleteMapping("/{vehicleId:[a-f0-9\\-]{36}}/clear-immediate-assignment")
    public Mono<ResponseEntity<Void>> clearImmediateAssignment(
            @PathVariable String vehicleId) {

        return clearImmediateAssignmentUseCase.execute(vehicleId)
                .then(noContent());
    }
}
