package biz.ugur.busroutebackend.transport.domain.repository;

import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface VehicleRepository {

    Mono<Vehicle> save(Vehicle vehicle);

    Mono<Vehicle> findById(VehicleId vehicleId);

    Mono<Vehicle> findByDeviceId(String deviceId);

    Mono<Vehicle> findByLicensePlate(String licensePlate);

    Flux<Vehicle> findByAssignedRouteId(BusRouteId routeId);

    Flux<Vehicle> findActiveVehicles();

    Flux<Vehicle> findByRouteNumber(String routeNumber);

    Flux<Vehicle> findUnassignedVehicles();

    Flux<Vehicle> findVehiclesInMotion();

    Flux<Vehicle> findVehiclesWithinRadius(Double centerLat, Double centerLon, Integer radiusMeters);

    Flux<Vehicle> findVehiclesWithRecentPosition();

    Mono<Boolean> existsByDeviceId(String deviceId);

    Mono<Boolean> existsByLicensePlate(String licensePlate);

    Mono<Void> deleteById(VehicleId vehicleId);

    Mono<Long> countActiveVehicles();
}