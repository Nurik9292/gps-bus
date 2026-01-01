package biz.ugur.busroutebackend.transport.application.usecase.assignment;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.assignment.BatchCreateResult;
import biz.ugur.busroutebackend.transport.application.dto.assignment.BatchCreateRouteAssignmentCommand;
import biz.ugur.busroutebackend.transport.application.dto.assignment.CreateRouteAssignmentCommand;
import biz.ugur.busroutebackend.transport.application.dto.assignment.RouteAssignmentData;
import biz.ugur.busroutebackend.transport.application.mapper.RouteAssignmentDataMapper;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteSource;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class BatchCreateRouteAssignmentsUseCase extends BaseUseCase<Mono<BatchCreateRouteAssignmentCommand>, BatchCreateResult> {

    private final RouteAssignmentRepository assignmentRepository;
    private final VehicleRepository vehicleRepository;
    private final BusRouteRepository busRouteRepository;
    private final RouteAssignmentDataMapper dataMapper;

    public BatchCreateRouteAssignmentsUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            RouteAssignmentRepository assignmentRepository,
            VehicleRepository vehicleRepository,
            BusRouteRepository busRouteRepository,
            RouteAssignmentDataMapper dataMapper) {
        super(correlationService, eventBus);
        this.assignmentRepository = assignmentRepository;
        this.vehicleRepository = vehicleRepository;
        this.busRouteRepository = busRouteRepository;
        this.dataMapper = dataMapper;
    }

    @Override
    protected Mono<BatchCreateResult> process(Mono<BatchCreateRouteAssignmentCommand> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<BatchCreateResult> processInternal(BatchCreateRouteAssignmentCommand command) {
        log.info("Batch creating route assignments: vehicles={}, route={}, date={}, shift={}",
                command.vehicleIds().size(), command.routeId(), command.effectiveDate(), command.shiftType());

        List<RouteAssignmentData> created = new ArrayList<>();
        List<BatchCreateResult.FailedAssignment> failed = new ArrayList<>();

        return Flux.fromIterable(command.vehicleIds())
                .flatMap(vehicleId -> createSingleAssignment(vehicleId, command)
                        .flatMap(assignment -> dataMapper.toRouteAssignmentData(assignment)
                                .doOnSuccess(created::add))
                        .onErrorResume(error -> {
                            log.warn("Failed to create assignment for vehicle {}: {}",
                                    vehicleId, error.getMessage());
                            failed.add(new BatchCreateResult.FailedAssignment(vehicleId, error.getMessage()));
                            return Mono.empty();
                        }))
                .collectList()
                .map(results -> new BatchCreateResult(
                        created,
                        failed,
                        created.size(),
                        failed.size(),
                        command.vehicleIds().size()
                ))
                .doOnSuccess(result -> log.info("Batch create completed: success={}, failed={}",
                        result.successCount(), result.failedCount()));
    }

    private Mono<RouteAssignment> createSingleAssignment(String vehicleIdStr, BatchCreateRouteAssignmentCommand command) {
        VehicleId vehicleId = VehicleId.of(vehicleIdStr);
        BusRouteId routeId = BusRouteId.of(command.routeId());
        ShiftType shiftType = ShiftType.fromString(command.shiftType());

        return validateVehicleExists(vehicleId)
                .then(Mono.defer(() -> checkNoConflictingAssignment(vehicleId, command, shiftType)))
                .then(Mono.defer(() -> {
                    RouteAssignment assignment = RouteAssignment.create(
                            vehicleId,
                            routeId,
                            command.effectiveDate(),
                            shiftType,
                            command.assignedBy(),
                            command.reason(),
                            command.expiresAt()
                    );
                    return assignmentRepository.save(assignment);
                }))
                .flatMap(assignment -> applyImmediatelyIfCurrentShift(assignment)
                        .thenReturn(assignment));
    }

    private Mono<Void> validateVehicleExists(VehicleId vehicleId) {
        return vehicleRepository.existsById(vehicleId)
                .flatMap(exists -> {
                    if (!exists) {
                        return Mono.error(new IllegalArgumentException(
                                "Vehicle not found: " + vehicleId.getValue()));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> checkNoConflictingAssignment(VehicleId vehicleId,
                                                     BatchCreateRouteAssignmentCommand command,
                                                     ShiftType shiftType) {
        return assignmentRepository.existsActiveByVehicleAndDateAndShift(
                        vehicleId, command.effectiveDate(), shiftType)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException(
                                "Vehicle already has assignment for this date and shift"));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> applyImmediatelyIfCurrentShift(RouteAssignment assignment) {
        if (!assignment.isForCurrentShift()) {
            return Mono.empty();
        }

        return vehicleRepository.findById(assignment.getVehicleId())
                .filter(vehicle -> Boolean.TRUE.equals(vehicle.getIsActive()))
                .flatMap(vehicle -> busRouteRepository.findById(assignment.getRouteId())
                        .map(route -> vehicle.toBuilder()
                                .assignedRouteId(assignment.getRouteId())
                                .routeNumber(route.getRouteNumber())
                                .routeSource(RouteSource.SHIFT_ASSIGNMENT)
                                .routeConfidence(100)
                                .build())
                        .flatMap(vehicleRepository::save))
                .then()
                .onErrorResume(error -> {
                    log.warn("Failed to apply assignment immediately: {}", error.getMessage());
                    return Mono.empty();
                });
    }
}
