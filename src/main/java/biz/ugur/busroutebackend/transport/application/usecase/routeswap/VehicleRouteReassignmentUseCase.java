package biz.ugur.busroutebackend.transport.application.usecase.routeswap;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.SecurityContextService;
import biz.ugur.busroutebackend.transport.application.dto.assignment.CreateRouteAssignmentCommand;
import biz.ugur.busroutebackend.transport.application.dto.assignment.RouteAssignmentData;
import biz.ugur.busroutebackend.transport.application.dto.assignment.UpdateRouteAssignmentCommand;
import biz.ugur.busroutebackend.transport.application.dto.routeswap.ReassignVehicleCommand;
import biz.ugur.busroutebackend.transport.application.dto.routeswap.VehicleReassignmentDTO;
import biz.ugur.busroutebackend.transport.application.usecase.assignment.CreateRouteAssignmentUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.assignment.UpdateRouteAssignmentUseCase;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.exceptions.AssignmentValidationException;
import biz.ugur.busroutebackend.transport.domain.exceptions.BusRouteNotFoundException;
import biz.ugur.busroutebackend.transport.domain.exceptions.VehicleNotFoundException;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteSwapAuditRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteSwapAuditRepository.AssignmentChange;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Service
public class VehicleRouteReassignmentUseCase {

    static final String OPERATOR_REASSIGN = "OPERATOR_REASSIGN";
    static final String OPERATOR_REVERT = "OPERATOR_REVERT";
    private static final String REVERT_REASON = "Откат ручного переназначения";
    private static final String BOUND_CONTEXT = "transport";

    private final VehicleRepository vehicleRepository;
    private final BusRouteRepository busRouteRepository;
    private final RouteAssignmentRepository assignmentRepository;
    private final CreateRouteAssignmentUseCase createUseCase;
    private final UpdateRouteAssignmentUseCase updateUseCase;
    private final RouteSwapAuditRepository auditRepository;
    private final SecurityContextService securityContextService;
    private final CorrelationContextService correlationService;
    private final Clock clock;

    public VehicleRouteReassignmentUseCase(VehicleRepository vehicleRepository,
                                                BusRouteRepository busRouteRepository,
                                                RouteAssignmentRepository assignmentRepository,
                                                CreateRouteAssignmentUseCase createUseCase,
                                                UpdateRouteAssignmentUseCase updateUseCase,
                                                RouteSwapAuditRepository auditRepository,
                                                SecurityContextService securityContextService,
                                                CorrelationContextService correlationService,
                                                Clock clock) {
        this.vehicleRepository = vehicleRepository;
        this.busRouteRepository = busRouteRepository;
        this.assignmentRepository = assignmentRepository;
        this.createUseCase = createUseCase;
        this.updateUseCase = updateUseCase;
        this.auditRepository = auditRepository;
        this.securityContextService = securityContextService;
        this.correlationService = correlationService;
        this.clock = clock;
    }

    public Mono<VehicleReassignmentDTO> reassign(ReassignVehicleCommand command) {
        Instant now = clock.instant();
        LocalDate operationalDate = ShiftType.operationalDateAt(now);
        return correlationService.executeWithCorrelation(
                Mono.defer(() -> currentShift(now)
                        .flatMap(shift -> securityContextService.getCurrentUsername()
                                .flatMap(actor -> loadVehicle(command.vehicleId())
                                        .flatMap(vehicle -> applyReassignment(
                                                vehicle, command, shift, operationalDate, actor))))),
                BOUND_CONTEXT);
    }

    public Mono<VehicleReassignmentDTO> revert(String vehicleId) {
        Instant now = clock.instant();
        LocalDate operationalDate = ShiftType.operationalDateAt(now);
        return correlationService.executeWithCorrelation(
                Mono.defer(() -> currentShift(now)
                        .flatMap(shift -> securityContextService.getCurrentUsername()
                                .flatMap(actor -> lastOperatorReassign(vehicleId, shift, operationalDate)
                                        .flatMap(change -> applyRevert(
                                                change, shift, operationalDate, actor))))),
                BOUND_CONTEXT);
    }

    private Mono<AssignmentChange> lastOperatorReassign(String vehicleId, ShiftType shift,
                                                        LocalDate operationalDate) {
        return auditRepository.findLastOperatorReassign(vehicleId, shift.startInstantOn(operationalDate))
                .switchIfEmpty(Mono.error(() -> new AssignmentValidationException(
                        "No operator reassignment to revert in current shift for vehicle " + vehicleId)));
    }

    private Mono<VehicleReassignmentDTO> applyRevert(AssignmentChange change, ShiftType shift,
                                                     LocalDate operationalDate, String actor) {
        if (change.previousRouteId() == null) {
            return Mono.error(new AssignmentValidationException(
                    "Reassignment had no previous route, nothing to revert: " + change.licensePlate()));
        }
        Instant expiresAt = shift.endInstantOn(operationalDate);
        return loadVehicle(change.vehicleId())
                .flatMap(vehicle -> busRouteRepository.findById(new BusRouteId(change.previousRouteId()))
                        .switchIfEmpty(Mono.error(() -> BusRouteNotFoundException.byId(change.previousRouteId())))
                        .flatMap(route -> persistAssignment(vehicle, route, REVERT_REASON, shift,
                                        operationalDate, actor, expiresAt)
                                .then(Mono.defer(() -> auditRepository.logOperatorAssignmentChange(
                                        change.vehicleId(), change.licensePlate(), change.newRouteId(),
                                        change.previousRouteId(), OPERATOR_REVERT, actor)))
                                .thenReturn(revertOf(change, route, operationalDate, shift, expiresAt))));
    }

    private static VehicleReassignmentDTO revertOf(AssignmentChange change, BusRoute route,
                                                   LocalDate operationalDate, ShiftType shift,
                                                   Instant expiresAt) {
        return new VehicleReassignmentDTO(
                change.vehicleId(), change.licensePlate(),
                change.newRouteId(), null,
                route.getId().getValue(), route.getRouteNumber(),
                operationalDate, shift.name(), expiresAt);
    }

    private Mono<ShiftType> currentShift(Instant now) {
        return ShiftType.operationalShiftAt(now)
                .map(Mono::just)
                .orElseGet(() -> Mono.error(new AssignmentValidationException(
                        "Reassignment is allowed only during an operational shift")));
    }

    private Mono<Vehicle> loadVehicle(String vehicleId) {
        return vehicleRepository.findById(VehicleId.of(vehicleId))
                .switchIfEmpty(Mono.error(() -> new VehicleNotFoundException(vehicleId)));
    }

    private Mono<VehicleReassignmentDTO> applyReassignment(Vehicle vehicle, ReassignVehicleCommand command,
                                                           ShiftType shift, LocalDate operationalDate,
                                                           String actor) {
        if (vehicle.getCityId() == null) {
            return Mono.error(new AssignmentValidationException(
                    "Vehicle has no city, route number cannot be resolved: " + vehicle.getLicensePlate()));
        }
        Instant expiresAt = shift.endInstantOn(operationalDate);
        return busRouteRepository.findByRouteNumberAndCityId(command.routeNumber(), vehicle.getCityId().getValue())
                .switchIfEmpty(Mono.error(() -> new BusRouteNotFoundException(
                        "Bus route not found by number " + command.routeNumber()
                                + " in city " + vehicle.getCityId().getValue())))
                .flatMap(route -> persistAssignment(vehicle, route, command.reason(), shift, operationalDate,
                                actor, expiresAt)
                        .then(Mono.defer(() -> logOperatorChange(vehicle, route, actor)))
                        .thenReturn(reassignmentOf(vehicle, route, operationalDate, shift, expiresAt)));
    }

    private Mono<RouteAssignmentData> persistAssignment(Vehicle vehicle, BusRoute route, String reason,
                                                        ShiftType shift, LocalDate operationalDate,
                                                        String actor, Instant expiresAt) {
        return assignmentRepository.findActiveByVehicleAndDateAndShift(vehicle.getId(), operationalDate, shift)
                .flatMap(existing -> updateUseCase.execute(Mono.just(new UpdateRouteAssignmentCommand(
                        existing.getId().getValue(), null, route.getId().getValue(), null, null,
                        reason, expiresAt, true))))
                .switchIfEmpty(Mono.defer(() -> createUseCase.execute(Mono.just(new CreateRouteAssignmentCommand(
                        vehicle.getId().getValue(), route.getId().getValue(), operationalDate,
                        shift.name(), actor, reason, expiresAt)))));
    }

    private Mono<Void> logOperatorChange(Vehicle vehicle, BusRoute route, String actor) {
        return auditRepository.logOperatorAssignmentChange(
                vehicle.getId().getValue(), vehicle.getLicensePlate(),
                previousRouteId(vehicle), route.getId().getValue(), OPERATOR_REASSIGN, actor);
    }

    private static String previousRouteId(Vehicle vehicle) {
        return vehicle.getAssignedRouteId() == null ? null : vehicle.getAssignedRouteId().getValue();
    }

    private static VehicleReassignmentDTO reassignmentOf(Vehicle vehicle, BusRoute route,
                                                         LocalDate operationalDate, ShiftType shift,
                                                         Instant expiresAt) {
        return new VehicleReassignmentDTO(
                vehicle.getId().getValue(), vehicle.getLicensePlate(),
                previousRouteId(vehicle), vehicle.getRouteNumber(),
                route.getId().getValue(), route.getRouteNumber(),
                operationalDate, shift.name(), expiresAt);
    }
}
