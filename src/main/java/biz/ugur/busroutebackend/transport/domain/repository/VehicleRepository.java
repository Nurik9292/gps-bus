package biz.ugur.busroutebackend.transport.domain.repository;

import biz.ugur.busroutebackend.shared.base.BaseRepository;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsProviderType;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import org.springframework.data.domain.Pageable;
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


    Mono<Map<String, Vehicle>> findByDeviceIds(List<String> deviceIds);

    Flux<String> findAllDeviceIds();

    Mono<Integer> batchUpdate(List<Vehicle> vehicles);

    Flux<Vehicle> batchInsert(List<Vehicle> vehicles);

    Flux<Vehicle> findBySpecification(Specification<Vehicle> specification);

    Flux<Vehicle> findBySpecification(Specification<Vehicle> specification, Pageable pageable);

    Mono<Long> countBySpecification(Specification<Vehicle> specification);

    Mono<Long> clearAllRouteAssignments();

    Mono<Long> clearRouteAssignmentsExcluding(List<VehicleId> excludeVehicleIds);

    Mono<Map<String, Vehicle>> findAllWithRouteAssignment();

    Mono<Integer> updateRouteAssignment(String licensePlate, BusRouteId routeId, String routeNumber);

    Mono<Integer> clearRouteAssignmentsByLicensePlates(List<String> licensePlates);

    Mono<Map<GpsProviderType, List<String>>> findAllDeviceIdsGroupedByProvider();

    Flux<String> findDeviceIdsByProvider(GpsProviderType providerType);

    Mono<Integer> updateGpsProvider(VehicleId vehicleId, GpsProviderType providerType);
}