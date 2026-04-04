package biz.ugur.busroutebackend.transport.domain.repository;

import biz.ugur.busroutebackend.shared.base.BaseRepository;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteAssignmentId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface RouteAssignmentRepository extends BaseRepository<RouteAssignment, RouteAssignmentId> {

    Mono<RouteAssignment> findActiveByVehicleAndDateAndShift(
            VehicleId vehicleId,
            LocalDate effectiveDate,
            ShiftType shiftType
    );

    Flux<RouteAssignment> findActiveByVehicleId(VehicleId vehicleId);

    Flux<RouteAssignment> findActiveByDateAndShift(
            LocalDate effectiveDate,
            ShiftType shiftType
    );

    Flux<RouteAssignment> findActiveByRouteId(BusRouteId routeId);

    Flux<RouteAssignment> findExpiredAssignments();

    Flux<RouteAssignment> findActiveByEffectiveDateBefore(LocalDate date);

    Flux<RouteAssignment> findActiveByAssignedBy(String assignedBy);

    Mono<Boolean> existsActiveByVehicleAndDateAndShift(
            VehicleId vehicleId,
            LocalDate effectiveDate,
            ShiftType shiftType
    );

    Mono<Integer> deactivateAllByVehicleId(VehicleId vehicleId);

    Mono<RouteAssignment> deactivateById(RouteAssignmentId id);

    Mono<Integer> deleteByEffectiveDateBefore(LocalDate date);

    Mono<Integer> deleteByEffectiveDate(LocalDate date);

    Mono<Integer> deleteByEffectiveDateAndShift(LocalDate date, ShiftType shiftType);
}
