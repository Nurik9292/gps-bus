package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.vehicle.VehicleCreateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.vehicle.VehicleUpdateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.vehicle.VehicleListResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.vehicle.VehicleResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.vehicle.VehicleSelectOption;
import biz.ugur.busroutebackend.shared.infrastructure.web.BasePaginatedController;
import biz.ugur.busroutebackend.transport.application.usecase.vehicle.*;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_VEHICLES;

@RestController
@RequestMapping(V1_ADMIN_VEHICLES)
@CrossOrigin(origins = "*")
public class AdminVehicleController extends BasePaginatedController {

    private final CreateVehicleUseCase createVehicleUseCase;
    private final UpdateVehicleUseCase updateVehicleUseCase;
    private final DeleteVehicleUseCase deleteVehicleUseCase;
    private final GetVehicleByIdUseCase getVehicleByIdUseCase;
    private final GetAllVehiclesWithPaginationUseCase getAllVehiclesUseCase;
    private final VehicleRepository vehicleRepository;

    public AdminVehicleController(
            CreateVehicleUseCase createVehicleUseCase,
            UpdateVehicleUseCase updateVehicleUseCase,
            DeleteVehicleUseCase deleteVehicleUseCase,
            GetVehicleByIdUseCase getVehicleByIdUseCase,
            GetAllVehiclesWithPaginationUseCase getAllVehiclesUseCase,
            VehicleRepository vehicleRepository,
            MessageSource messageSource) {
        super(messageSource);
        this.createVehicleUseCase = createVehicleUseCase;
        this.updateVehicleUseCase = updateVehicleUseCase;
        this.deleteVehicleUseCase = deleteVehicleUseCase;
        this.getVehicleByIdUseCase = getVehicleByIdUseCase;
        this.getAllVehiclesUseCase = getAllVehiclesUseCase;
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    protected String getControllerName() {
        return AdminVehicleController.class.getSimpleName();
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<VehicleListResponse>>> getAllVehicles(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String query) {

        validatePagination(page, size);

        return ok(Mono.just(GetAllVehiclesWithPaginationUseCase.Query.fromParams(
                        page, size, camelToSnake(sort), order, active, query))
                .as(getAllVehiclesUseCase::execute)
                .map(VehicleListResponse::fromResult));
    }

    /**
     * Lightweight endpoint for select boxes.
     * Returns only id, deviceId, and licensePlate for all active vehicles.
     */
    @GetMapping("/select-options")
    public Mono<ResponseEntity<ApiResponse<List<VehicleSelectOption>>>> getSelectOptions() {
        return ok(vehicleRepository.findActiveVehicles()
                .map(vehicle -> new VehicleSelectOption(
                        vehicle.getId().getValue(),
                        vehicle.getDeviceId(),
                        vehicle.getLicensePlate()
                ))
                .collectList());
    }

    @GetMapping("/{id:[a-f0-9\\-]{36}}")
    public Mono<ResponseEntity<ApiResponse<VehicleResponse>>> getVehicleById(@PathVariable String id) {
        return ok(Mono.just(id)
                .as(getVehicleByIdUseCase::execute)
                .map(VehicleResponse::fromData));
    }

    @PostMapping
    public Mono<ResponseEntity<ApiResponse<VehicleResponse>>> createVehicle(
            @Valid @RequestBody VehicleCreateRequest request) {
        return created(Mono.just(request.toCommand())
                .as(createVehicleUseCase::execute)
                .map(VehicleResponse::fromData));
    }

    @PutMapping("/{id:[a-f0-9\\-]{36}}")
    public Mono<ResponseEntity<ApiResponse<VehicleResponse>>> updateVehicle(
            @PathVariable String id,
            @Valid @RequestBody VehicleUpdateRequest request) {
        return ok(Mono.just(request.toCommand(id))
                .as(updateVehicleUseCase::execute)
                .map(VehicleResponse::fromData));
    }

    @DeleteMapping("/{id:[a-f0-9\\-]{36}}")
    public Mono<ResponseEntity<Void>> deleteVehicle(@PathVariable String id) {
        return deleteVehicleUseCase.execute(Mono.just(id))
                .then(noContent());
    }
}
