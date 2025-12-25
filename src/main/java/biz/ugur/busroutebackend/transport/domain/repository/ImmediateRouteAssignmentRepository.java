package biz.ugur.busroutebackend.transport.domain.repository;

import biz.ugur.busroutebackend.transport.domain.model.ImmediateRouteAssignment;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.ImmediateAssignmentId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ImmediateRouteAssignmentRepository {

    Mono<ImmediateRouteAssignment> save(ImmediateRouteAssignment assignment);

    Mono<ImmediateRouteAssignment> findById(ImmediateAssignmentId id);

    Mono<ImmediateRouteAssignment> findActiveByVehicleId(VehicleId vehicleId);

    Flux<ImmediateRouteAssignment> findAllActive();

    Flux<ImmediateRouteAssignment> findByVehicleId(VehicleId vehicleId);

    Flux<ImmediateRouteAssignment> findByRouteId(BusRouteId routeId);

    Flux<ImmediateRouteAssignment> findExpired();

    Mono<Void> deleteById(ImmediateAssignmentId id);

    Mono<Long> countActive();

    Mono<Integer> deactivateByVehicleId(VehicleId vehicleId);

    Mono<Integer> deactivateAll();
}
