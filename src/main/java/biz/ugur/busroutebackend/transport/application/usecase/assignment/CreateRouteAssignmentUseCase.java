package biz.ugur.busroutebackend.transport.application.usecase.assignment;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.assignment.CreateRouteAssignmentCommand;
import biz.ugur.busroutebackend.transport.application.dto.assignment.RouteAssignmentData;
import biz.ugur.busroutebackend.transport.application.mapper.RouteAssignmentDataMapper;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.exceptions.AssignmentValidationException;
import biz.ugur.busroutebackend.transport.domain.exceptions.BusRouteNotFoundException;
import biz.ugur.busroutebackend.transport.domain.exceptions.VehicleNotFoundException;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteSource;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;

@Service
@Slf4j
public class CreateRouteAssignmentUseCase extends BaseUseCase<Mono<CreateRouteAssignmentCommand>, RouteAssignmentData> {

    private final RouteAssignmentRepository assignmentRepository;
    private final VehicleRepository vehicleRepository;
    private final BusRouteRepository busRouteRepository;
    private final RouteAssignmentDataMapper dataMapper;

    public CreateRouteAssignmentUseCase(
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
    protected Mono<RouteAssignmentData> process(Mono<CreateRouteAssignmentCommand> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<RouteAssignmentData> processInternal(CreateRouteAssignmentCommand command) {
        VehicleId vehicleId = VehicleId.of(command.vehicleId());
        BusRouteId routeId = BusRouteId.of(command.routeId());
        LocalDate effectiveDate = command.effectiveDate();
        ShiftType shiftType = ShiftType.fromString(command.shiftType());
        Instant expiresAt = command.expiresAt();

        log.info("Creating route assignment: vehicle={}, route={}, date={}, shift={}, expiresAt={}",
                command.vehicleId(), command.routeId(), effectiveDate, shiftType, expiresAt);

        return validateVehicleExists(vehicleId)
                .then(Mono.defer(() -> validateRouteExists(routeId)))
                .then(Mono.defer(() -> checkNoConflictingAssignment(vehicleId, effectiveDate, shiftType)))
                .then(Mono.defer(() -> createAssignment(vehicleId, routeId, effectiveDate, shiftType,
                        command.assignedBy(), command.reason(), expiresAt)))
                .flatMap(assignment -> applyImmediatelyIfCurrentShift(assignment)
                        .thenReturn(assignment))
                .flatMap(dataMapper::toRouteAssignmentData)
                .doOnSuccess(data -> {
                    if (data != null) {
                        log.info("Created route assignment: id={}, isImmediate={}, time={}-{}",
                                data.id(), data.isForCurrentShift(),
                                data.startTime(), data.endTime());
                    }
                })
                .doOnError(error -> log.error("Failed to create route assignment", error));
    }

    private Mono<Void> validateVehicleExists(VehicleId vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .switchIfEmpty(Mono.error(() -> new VehicleNotFoundException(vehicleId.getValue())))
                .flatMap(vehicle -> {
                    if (!Boolean.TRUE.equals(vehicle.getIsActive())) {
                        return Mono.error(new AssignmentValidationException(
                                "Vehicle is not active: " + vehicleId.getValue()));
                    }
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> validateRouteExists(BusRouteId routeId) {
        return busRouteRepository.findById(routeId)
                .switchIfEmpty(Mono.error(() -> new BusRouteNotFoundException(routeId.getValue())))
                .flatMap(route -> {
                    if (!Boolean.TRUE.equals(route.getIsActive())) {
                        return Mono.error(new AssignmentValidationException(
                                "Route is not active: " + routeId.getValue()));
                    }
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> checkNoConflictingAssignment(VehicleId vehicleId,
                                                      LocalDate effectiveDate,
                                                      ShiftType shiftType) {
        return assignmentRepository.existsActiveByVehicleAndDateAndShift(vehicleId, effectiveDate, shiftType)
                .flatMap(exists -> {
                    if (exists) {
                        String message = String.format(
                                "Vehicle %s already has an active assignment for %s %s shift",
                                vehicleId.getValue(), effectiveDate, shiftType);
                        return Mono.<Void>error(new AssignmentValidationException(message));
                    }
                    return Mono.empty();
                })
                .then();
    }

    private Mono<RouteAssignment> createAssignment(VehicleId vehicleId,
                                                     BusRouteId routeId,
                                                     LocalDate effectiveDate,
                                                     ShiftType shiftType,
                                                     String assignedBy,
                                                     String reason,
                                                     Instant expiresAt) {
        try {
            RouteAssignment assignment = RouteAssignment.create(
                    vehicleId,
                    routeId,
                    effectiveDate,
                    shiftType,
                    assignedBy,
                    reason,
                    expiresAt
            );

            return assignmentRepository.save(assignment);
        } catch (IllegalArgumentException e) {
            return Mono.error(new AssignmentValidationException(e.getMessage()));
        }
    }

    private Mono<Void> applyImmediatelyIfCurrentShift(RouteAssignment assignment) {
        if (!assignment.isForCurrentShift()) {
            log.debug("Assignment is scheduled for future: date={}, shift={}, will be applied by scheduler",
                    assignment.getEffectiveDate(), assignment.getShiftType());
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
                    log.warn("Failed to apply assignment immediately, but assignment was saved: {}",
                            error.getMessage());
                    return Mono.empty();
                });
    }
}
