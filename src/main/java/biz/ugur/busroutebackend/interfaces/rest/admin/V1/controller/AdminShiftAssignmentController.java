package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.shift.ShiftAssignmentCreateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.shift.ShiftAssignmentUpdateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.shift.ShiftAssignmentListResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.shift.ShiftAssignmentResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.shift.VehicleShiftsResponse;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import biz.ugur.busroutebackend.transport.application.usecase.shift.*;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_SHIFT_ASSIGNMENTS;

@RestController
@RequestMapping(V1_ADMIN_SHIFT_ASSIGNMENTS)
@CrossOrigin(origins = "*")
public class AdminShiftAssignmentController extends BaseController {

    private final CreateShiftAssignmentUseCase createUseCase;
    private final UpdateShiftAssignmentUseCase updateUseCase;
    private final DeleteShiftAssignmentUseCase deleteUseCase;
    private final GetShiftAssignmentByIdUseCase getByIdUseCase;
    private final GetAllShiftAssignmentsUseCase getAllUseCase;
    private final GetVehicleShiftsUseCase getVehicleShiftsUseCase;

    public AdminShiftAssignmentController(
            CreateShiftAssignmentUseCase createUseCase,
            UpdateShiftAssignmentUseCase updateUseCase,
            DeleteShiftAssignmentUseCase deleteUseCase,
            GetShiftAssignmentByIdUseCase getByIdUseCase,
            GetAllShiftAssignmentsUseCase getAllUseCase,
            GetVehicleShiftsUseCase getVehicleShiftsUseCase,
            MessageSource messageSource) {
        super(messageSource);
        this.createUseCase = createUseCase;
        this.updateUseCase = updateUseCase;
        this.deleteUseCase = deleteUseCase;
        this.getByIdUseCase = getByIdUseCase;
        this.getAllUseCase = getAllUseCase;
        this.getVehicleShiftsUseCase = getVehicleShiftsUseCase;
    }

    @Override
    protected String getControllerName() {
        return AdminShiftAssignmentController.class.getSimpleName();
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<ShiftAssignmentListResponse>>> getAllAssignments() {
        return ok(getAllUseCase.execute(Mono.empty())
                .map(ShiftAssignmentListResponse::fromData));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<ShiftAssignmentResponse>>> getAssignmentById(@PathVariable String id) {
        return ok(Mono.just(id)
                .as(getByIdUseCase::execute)
                .map(ShiftAssignmentResponse::fromData));
    }

    @GetMapping("/vehicle/{vehicleId}")
    public Mono<ResponseEntity<ApiResponse<VehicleShiftsResponse>>> getVehicleShifts(@PathVariable String vehicleId) {
        return ok(Mono.just(vehicleId)
                .as(getVehicleShiftsUseCase::execute)
                .map(VehicleShiftsResponse::fromData));
    }

    @PostMapping
    public Mono<ResponseEntity<ApiResponse<ShiftAssignmentResponse>>> createAssignment(
            @Valid @RequestBody ShiftAssignmentCreateRequest request) {
        return created(Mono.just(request.toCommand())
                .as(createUseCase::execute)
                .map(ShiftAssignmentResponse::fromData));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<ShiftAssignmentResponse>>> updateAssignment(
            @PathVariable String id,
            @Valid @RequestBody ShiftAssignmentUpdateRequest request) {
        return ok(Mono.just(new UpdateShiftAssignmentUseCase.Command(id, request.toCommand()))
                .as(updateUseCase::execute)
                .map(ShiftAssignmentResponse::fromData));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteAssignment(@PathVariable String id) {
        return deleteUseCase.execute(Mono.just(id))
                .then(noContent());
    }
}
