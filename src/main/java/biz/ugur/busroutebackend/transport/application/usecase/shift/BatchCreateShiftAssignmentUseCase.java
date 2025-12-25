package biz.ugur.busroutebackend.transport.application.usecase.shift;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.shift.CreateShiftAssignment;
import biz.ugur.busroutebackend.transport.application.dto.shift.ShiftAssignmentData;
import biz.ugur.busroutebackend.transport.application.mapper.ShiftAssignmentDataMapper;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.VehicleShiftAssignment;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleShiftAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;


@Service
@Slf4j
public class BatchCreateShiftAssignmentUseCase extends BaseUseCase<BatchCreateShiftAssignmentUseCase.Command, BatchCreateShiftAssignmentUseCase.Result> {

    private final VehicleShiftAssignmentRepository shiftAssignmentRepository;
    private final VehicleRepository vehicleRepository;
    private final BusRouteRepository busRouteRepository;
    private final ShiftAssignmentDataMapper dataMapper;

    public BatchCreateShiftAssignmentUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            VehicleShiftAssignmentRepository shiftAssignmentRepository,
            VehicleRepository vehicleRepository,
            BusRouteRepository busRouteRepository,
            ShiftAssignmentDataMapper dataMapper) {
        super(correlationService, eventBus);
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.vehicleRepository = vehicleRepository;
        this.busRouteRepository = busRouteRepository;
        this.dataMapper = dataMapper;
    }

    @Override
    protected Mono<Result> process(Command command) {
        if (command.vehicleIds() == null || command.vehicleIds().isEmpty()) {
            return Mono.just(new Result(List.of(), List.of(), 0, 0));
        }

        BusRouteId routeId = BusRouteId.of(command.routeId());
        ShiftType shiftType = ShiftType.fromString(command.shiftType());
        String assignedBy = command.assignedBy();

        return busRouteRepository.findById(routeId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Route not found: " + command.routeId())))
                .flatMap(route -> processVehicles(command.vehicleIds(), routeId, shiftType, assignedBy));
    }

    private Mono<Result> processVehicles(List<String> vehicleIds, BusRouteId routeId, ShiftType shiftType, String assignedBy) {
        return Flux.fromIterable(vehicleIds)
                .flatMap(vehicleIdStr -> processVehicle(vehicleIdStr, routeId, shiftType, assignedBy))
                .collectList()
                .map(results -> {
                    List<ShiftAssignmentData> successes = results.stream()
                            .filter(VehicleResult::success)
                            .map(VehicleResult::data)
                            .toList();

                    List<FailedAssignment> failures = results.stream()
                            .filter(r -> !r.success())
                            .map(r -> new FailedAssignment(r.vehicleId(), r.error()))
                            .toList();

                    log.info("Batch assignment completed: {} succeeded, {} failed",
                            successes.size(), failures.size());

                    return new Result(successes, failures, successes.size(), failures.size());
                });
    }

    private Mono<VehicleResult> processVehicle(String vehicleIdStr, BusRouteId routeId, ShiftType shiftType, String assignedBy) {
        VehicleId vehicleId = VehicleId.of(vehicleIdStr);

        return vehicleRepository.findById(vehicleId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Vehicle not found")))
                .flatMap(vehicle -> shiftAssignmentRepository.existsByVehicleIdAndShiftType(vehicleId, shiftType)
                        .flatMap(exists -> {
                            if (exists) {
                                // Delete existing and create new
                                return shiftAssignmentRepository.deleteByVehicleIdAndShiftType(vehicleId, shiftType)
                                        .then(createAndSave(vehicleId, routeId, shiftType, assignedBy));
                            }
                            return createAndSave(vehicleId, routeId, shiftType, assignedBy);
                        }))
                .flatMap(assignment -> dataMapper.toShiftAssignmentData(assignment)
                        .map(data -> new VehicleResult(vehicleIdStr, true, data, null)))
                .onErrorResume(error -> {
                    log.warn("Failed to assign vehicle {}: {}", vehicleIdStr, error.getMessage());
                    return Mono.just(new VehicleResult(vehicleIdStr, false, null, error.getMessage()));
                });
    }

    private Mono<VehicleShiftAssignment> createAndSave(VehicleId vehicleId, BusRouteId routeId, ShiftType shiftType, String assignedBy) {
        VehicleShiftAssignment assignment = VehicleShiftAssignment.create(vehicleId, routeId, shiftType, assignedBy);
        return shiftAssignmentRepository.save(assignment);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    public record Command(
            List<String> vehicleIds,
            String routeId,
            String shiftType,
            String assignedBy
    ) {}

    public record Result(
            List<ShiftAssignmentData> created,
            List<FailedAssignment> failed,
            int successCount,
            int failedCount
    ) {}

    public record FailedAssignment(
            String vehicleId,
            String error
    ) {}

    private record VehicleResult(
            String vehicleId,
            boolean success,
            ShiftAssignmentData data,
            String error
    ) {}
}
