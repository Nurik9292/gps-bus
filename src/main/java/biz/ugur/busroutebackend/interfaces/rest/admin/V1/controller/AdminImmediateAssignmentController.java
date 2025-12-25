package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.immediate.ImmediateAssignmentRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.immediate.ClearAllAssignmentsResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.immediate.ImmediateAssignmentListResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.immediate.ImmediateAssignmentResponse;
import biz.ugur.busroutebackend.shared.application.SecurityContextService;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import biz.ugur.busroutebackend.transport.application.usecase.immediate.ClearAllImmediateAssignmentsUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.immediate.ClearImmediateAssignmentUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.immediate.CreateImmediateAssignmentUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.immediate.GetAllImmediateAssignmentsUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.immediate.GetImmediateAssignmentUseCase;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_VEHICLES;


@Slf4j
@RestController
@RequestMapping(V1_ADMIN_VEHICLES)
@CrossOrigin(origins = "*")
public class AdminImmediateAssignmentController extends BaseController {

    private final CreateImmediateAssignmentUseCase createImmediateAssignmentUseCase;
    private final GetImmediateAssignmentUseCase getImmediateAssignmentUseCase;
    private final GetAllImmediateAssignmentsUseCase getAllImmediateAssignmentsUseCase;
    private final ClearImmediateAssignmentUseCase clearImmediateAssignmentUseCase;
    private final ClearAllImmediateAssignmentsUseCase clearAllImmediateAssignmentsUseCase;
    private final SecurityContextService securityContextService;

    public AdminImmediateAssignmentController(
            CreateImmediateAssignmentUseCase createImmediateAssignmentUseCase,
            GetImmediateAssignmentUseCase getImmediateAssignmentUseCase,
            GetAllImmediateAssignmentsUseCase getAllImmediateAssignmentsUseCase,
            ClearImmediateAssignmentUseCase clearImmediateAssignmentUseCase,
            ClearAllImmediateAssignmentsUseCase clearAllImmediateAssignmentsUseCase,
            SecurityContextService securityContextService,
            MessageSource messageSource) {
        super(messageSource);
        this.createImmediateAssignmentUseCase = createImmediateAssignmentUseCase;
        this.getImmediateAssignmentUseCase = getImmediateAssignmentUseCase;
        this.getAllImmediateAssignmentsUseCase = getAllImmediateAssignmentsUseCase;
        this.clearImmediateAssignmentUseCase = clearImmediateAssignmentUseCase;
        this.clearAllImmediateAssignmentsUseCase = clearAllImmediateAssignmentsUseCase;
        this.securityContextService = securityContextService;
    }

    @Override
    protected String getControllerName() {
        return AdminImmediateAssignmentController.class.getSimpleName();
    }


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


    @GetMapping("/immediate-assignments")
    public Mono<ResponseEntity<ApiResponse<ImmediateAssignmentListResponse>>> getAllImmediateAssignments() {
        return ok(getAllImmediateAssignmentsUseCase.execute(null)
                .map(ImmediateAssignmentListResponse::fromResult));
    }


    @GetMapping("/{vehicleId:[a-f0-9\\-]{36}}/immediate-assignment")
    public Mono<ResponseEntity<ApiResponse<ImmediateAssignmentResponse>>> getImmediateAssignment(
            @PathVariable String vehicleId) {

        return okOrNotFound(
                getImmediateAssignmentUseCase.execute(vehicleId)
                        .map(ImmediateAssignmentResponse::fromData)
        );
    }

    @DeleteMapping("/{vehicleId:[a-f0-9\\-]{36}}/clear-immediate-assignment")
    public Mono<ResponseEntity<Void>> clearImmediateAssignment(
            @PathVariable String vehicleId) {

        return clearImmediateAssignmentUseCase.execute(vehicleId)
                .then(noContent());
    }


    @DeleteMapping("/clear-all-immediate-assignments")
    public Mono<ResponseEntity<ApiResponse<ClearAllAssignmentsResponse>>> clearAllImmediateAssignments() {
        log.info("Admin requested to clear all immediate assignments");

        return clearAllImmediateAssignmentsUseCase.execute(null)
                .map(result -> new ClearAllAssignmentsResponse(
                        result.clearedCount(),
                        result.clearedAt(),
                        result.success(),
                        result.errorMessage()
                ))
                .flatMap(response -> ok(Mono.just(response)));
    }
}
