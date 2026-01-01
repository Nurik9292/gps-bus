package biz.ugur.busroutebackend.transport.application.usecase.assignment;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.assignment.RouteAssignmentData;
import biz.ugur.busroutebackend.transport.application.dto.assignment.UpdateRouteAssignmentCommand;
import biz.ugur.busroutebackend.transport.application.mapper.RouteAssignmentDataMapper;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.exceptions.RouteAssignmentNotFoundException;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteAssignmentId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteSource;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class UpdateRouteAssignmentUseCase extends BaseUseCase<Mono<UpdateRouteAssignmentCommand>, RouteAssignmentData> {

    private final RouteAssignmentRepository assignmentRepository;
    private final VehicleRepository vehicleRepository;
    private final BusRouteRepository busRouteRepository;
    private final RouteAssignmentDataMapper dataMapper;

    public UpdateRouteAssignmentUseCase(
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
    protected Mono<RouteAssignmentData> process(Mono<UpdateRouteAssignmentCommand> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<RouteAssignmentData> processInternal(UpdateRouteAssignmentCommand command) {
        RouteAssignmentId assignmentId = RouteAssignmentId.of(command.id());

        log.info("Updating route assignment: id={}", command.id());

        return assignmentRepository.findById(assignmentId)
                .switchIfEmpty(Mono.error(() -> RouteAssignmentNotFoundException.byId(command.id())))
                .flatMap(existing -> updateAssignment(existing, command))
                .flatMap(assignmentRepository::save)
                .flatMap(assignment -> applyImmediatelyIfCurrentShift(assignment)
                        .thenReturn(assignment))
                .flatMap(dataMapper::toRouteAssignmentData)
                .doOnSuccess(data -> log.info("Updated route assignment: id={}", data.id()))
                .doOnError(error -> log.error("Failed to update route assignment: id={}", command.id(), error));
    }

    private Mono<RouteAssignment> updateAssignment(RouteAssignment existing, UpdateRouteAssignmentCommand command) {
        VehicleId vehicleId = command.vehicleId() != null
                ? VehicleId.of(command.vehicleId())
                : existing.getVehicleId();
        BusRouteId routeId = command.routeId() != null
                ? BusRouteId.of(command.routeId())
                : existing.getRouteId();
        ShiftType shiftType = command.shiftType() != null
                ? ShiftType.fromString(command.shiftType())
                : existing.getShiftType();

        RouteAssignment updated = existing.toBuilder()
                .vehicleId(vehicleId)
                .routeId(routeId)
                .effectiveDate(command.effectiveDate() != null ? command.effectiveDate() : existing.getEffectiveDate())
                .shiftType(shiftType)
                .reason(command.reason() != null ? command.reason() : existing.getReason())
                .expiresAt(command.expiresAt() != null ? command.expiresAt() : existing.getExpiresAt())
                .isActive(command.isActive() != null ? command.isActive() : existing.getIsActive())
                .build();

        return Mono.just(updated);
    }

    private Mono<Void> applyImmediatelyIfCurrentShift(RouteAssignment assignment) {
        if (!assignment.isForCurrentShift() || !Boolean.TRUE.equals(assignment.getIsActive())) {
            return Mono.empty();
        }

        log.info("Assignment is for current shift, applying immediately to vehicle: {}",
                assignment.getVehicleId());

        return vehicleRepository.findById(assignment.getVehicleId())
                .filter(vehicle -> Boolean.TRUE.equals(vehicle.getIsActive()))
                .flatMap(vehicle -> busRouteRepository.findById(assignment.getRouteId())
                        .map(route -> vehicle.toBuilder()
                                .assignedRouteId(assignment.getRouteId())
                                .routeNumber(route.getRouteNumber())
                                .routeSource(RouteSource.SHIFT_ASSIGNMENT)
                                .routeConfidence(100)
                                .build())
                        .flatMap(vehicleRepository::save)
                        .doOnSuccess(v -> log.info("Applied assignment immediately: vehicle={}, route={}",
                                v.getLicensePlate(), v.getRouteNumber()))
                )
                .then()
                .onErrorResume(error -> {
                    log.warn("Failed to apply assignment immediately: {}", error.getMessage());
                    return Mono.empty();
                });
    }
}
