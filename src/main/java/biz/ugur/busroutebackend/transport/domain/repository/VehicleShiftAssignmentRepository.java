package biz.ugur.busroutebackend.transport.domain.repository;

import biz.ugur.busroutebackend.shared.base.BaseRepository;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.VehicleShiftAssignment;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleShiftAssignmentId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface VehicleShiftAssignmentRepository extends BaseRepository<VehicleShiftAssignment, VehicleShiftAssignmentId> {

    Flux<VehicleShiftAssignment> findByVehicleId(VehicleId vehicleId);

    Flux<VehicleShiftAssignment> findByRouteId(BusRouteId routeId);

    Mono<VehicleShiftAssignment> findByVehicleIdAndShiftType(VehicleId vehicleId, ShiftType shiftType);

    Flux<VehicleShiftAssignment> findByShiftType(ShiftType shiftType);

    Flux<VehicleShiftAssignment> findActiveByShiftType(ShiftType shiftType);

    Flux<VehicleShiftAssignment> findAllActive();

    Mono<Boolean> existsByVehicleIdAndShiftType(VehicleId vehicleId, ShiftType shiftType);

    Mono<Void> deleteByVehicleIdAndShiftType(VehicleId vehicleId, ShiftType shiftType);

    Mono<Integer> deleteByVehicleId(VehicleId vehicleId);

    Mono<Long> countByRouteId(BusRouteId routeId);

    Mono<Long> countByShiftType(ShiftType shiftType);
}
