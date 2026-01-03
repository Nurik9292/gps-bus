package biz.ugur.busroutebackend.transport.infrastructure.services;

import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.RouteStopRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteStopRepository.VehiclePositionKey;
import biz.ugur.busroutebackend.transport.domain.service.VehicleDirectionDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


@Service
@Slf4j
@RequiredArgsConstructor
public class VehicleDirectionDetectionServiceImpl implements VehicleDirectionDetectionService {

    private final RouteStopRepository routeStopRepository;

    @Override
    public Mono<Integer> findNearestStopSequence(Vehicle vehicle) {
        if (vehicle == null || !vehicle.hasAssignedRoute() || !vehicle.hasPosition()) {
            return Mono.empty();
        }

        return routeStopRepository.findNearestStopSequence(
                vehicle.getAssignedRouteId().getValue(),
                vehicle.getCurrentLatitude(),
                vehicle.getCurrentLongitude(),
                vehicle.getCurrentDirection()
        );
    }

    @Override
    public Mono<Map<String, Integer>> findNearestStopSequencesBatch(List<Vehicle> vehicles) {
        if (vehicles == null || vehicles.isEmpty()) {
            return Mono.just(Map.of());
        }

        List<VehiclePositionKey> positionKeys = vehicles.stream()
                .filter(v -> v.hasAssignedRoute() && v.hasPosition())
                .map(v -> new VehiclePositionKey(
                        v.getId().getValue(),
                        v.getAssignedRouteId().getValue(),
                        v.getCurrentLatitude(),
                        v.getCurrentLongitude(),
                        v.getCurrentDirection()
                ))
                .toList();

        if (positionKeys.isEmpty()) {
            return Mono.just(Map.of());
        }

        return routeStopRepository.findNearestStopSequencesBatch(positionKeys);
    }

    @Override
    public Mono<Vehicle> updateVehicleDirection(Vehicle vehicle) {
        if (vehicle == null || !vehicle.hasAssignedRoute() || !vehicle.hasPosition()) {
            return Mono.justOrEmpty(vehicle);
        }

        return findNearestStopSequence(vehicle)
                .map(stopSequence -> {
                    Vehicle updatedVehicle = vehicle.updateDirection(stopSequence);
                    if (updatedVehicle.getCurrentDirection() != null &&
                            !updatedVehicle.getCurrentDirection().equals(vehicle.getCurrentDirection())) {
                        log.debug("Vehicle {} direction updated: {} (seq: {} -> {})",
                                vehicle.getLicensePlate(),
                                updatedVehicle.getCurrentDirection() == 0 ? "forward" : "backward",
                                vehicle.getLastStopSequence(),
                                stopSequence);
                    }
                    return updatedVehicle;
                })
                .defaultIfEmpty(vehicle);
    }

    @Override
    public Mono<List<Vehicle>> updateVehicleDirectionsBatch(List<Vehicle> vehicles) {
        if (vehicles == null || vehicles.isEmpty()) {
            return Mono.just(List.of());
        }

        List<Vehicle> vehiclesWithRoutes = new ArrayList<>();
        List<Vehicle> vehiclesWithoutRoutes = new ArrayList<>();

        for (Vehicle vehicle : vehicles) {
            if (vehicle.hasAssignedRoute() && vehicle.hasPosition()) {
                vehiclesWithRoutes.add(vehicle);
            } else {
                vehiclesWithoutRoutes.add(vehicle);
            }
        }

        if (vehiclesWithRoutes.isEmpty()) {
            return Mono.just(new ArrayList<>(vehiclesWithoutRoutes));
        }

        return findNearestStopSequencesBatch(vehiclesWithRoutes)
                .map(stopSequenceMap -> {
                    List<Vehicle> result = new ArrayList<>(vehiclesWithoutRoutes);

                    for (Vehicle vehicle : vehiclesWithRoutes) {
                        Integer stopSequence = stopSequenceMap.get(vehicle.getId().getValue());
                        if (stopSequence != null) {
                            Vehicle updatedVehicle = vehicle.updateDirection(stopSequence);
                            result.add(updatedVehicle);

                            if (updatedVehicle.getCurrentDirection() != null &&
                                    !updatedVehicle.getCurrentDirection().equals(vehicle.getCurrentDirection())) {
                                log.debug("Vehicle {} direction: {} (seq: {} -> {})",
                                        vehicle.getLicensePlate(),
                                        updatedVehicle.getCurrentDirection() == 0 ? "forward" : "backward",
                                        vehicle.getLastStopSequence(),
                                        stopSequence);
                            }
                        } else {
                            result.add(vehicle);
                        }
                    }

                    return result;
                });
    }
}
