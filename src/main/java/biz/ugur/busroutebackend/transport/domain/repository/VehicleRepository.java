package biz.ugur.busroutebackend.transport.domain.repository;

import biz.ugur.busroutebackend.shared.base.BaseRepository;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public interface VehicleRepository extends BaseRepository<Vehicle, VehicleId> {

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

    Mono<Long> countActiveVehicles();

    Mono<Long> countActiveVehiclesRouteNumber(String routeNumber);

    /**
     * Find vehicles by multiple device IDs in a single query
     * @param deviceIds list of device IDs to search for
     * @return Flux of found vehicles mapped by deviceId
     */
    Mono<Map<String, Vehicle>> findByDeviceIds(List<String> deviceIds);

    /**
     * Batch update vehicles positions
     * @param vehicles list of vehicles to update
     * @return Mono with number of updated vehicles
     */
    Mono<Integer> batchUpdate(List<Vehicle> vehicles);

    /**
     * Batch insert new vehicles
     * @param vehicles list of vehicles to insert
     * @return Flux of inserted vehicles
     */
    Flux<Vehicle> batchInsert(List<Vehicle> vehicles);
}