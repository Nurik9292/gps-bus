package biz.ugur.busroutebackend.transport.infrastructure.services;

import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.RouteStopRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteStopRepository.NearestStopResult;
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
    public Mono<NearestStopResult> findNearestStopSequence(Vehicle vehicle) {
        if (vehicle == null || !vehicle.hasAssignedRoute() || !vehicle.hasPosition()) {
            return Mono.empty();
        }

        if (vehicle.getCourse() != null && vehicle.getCourse() > 0) {
            return routeStopRepository.findDirectionByCourse(
                    vehicle.getAssignedRouteId().getValue(),
                    vehicle.getCurrentLatitude(),
                    vehicle.getCurrentLongitude(),
                    vehicle.getCourse(),
                    vehicle.getCurrentDirection()
            ).switchIfEmpty(
                    routeStopRepository.findNearestStopSequence(
                            vehicle.getAssignedRouteId().getValue(),
                            vehicle.getCurrentLatitude(),
                            vehicle.getCurrentLongitude(),
                            vehicle.getCurrentDirection()
                    )
            );
        }

        return routeStopRepository.findNearestStopSequence(
                vehicle.getAssignedRouteId().getValue(),
                vehicle.getCurrentLatitude(),
                vehicle.getCurrentLongitude(),
                vehicle.getCurrentDirection()
        );
    }

    @Override
    public Mono<Map<String, NearestStopResult>> findNearestStopSequencesBatch(List<Vehicle> vehicles) {
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
                        v.getCurrentDirection(),
                        v.getCourse()
                ))
                .toList();

        if (positionKeys.isEmpty()) {
            return Mono.just(Map.of());
        }

        List<VehiclePositionKey> withCourse = positionKeys.stream()
                .filter(p -> p.course() != null && p.course() > 0)
                .toList();

        List<VehiclePositionKey> withoutCourse = positionKeys.stream()
                .filter(p -> p.course() == null || p.course() <= 0)
                .toList();

        Mono<Map<String, NearestStopResult>> courseResults = withCourse.isEmpty()
                ? Mono.just(Map.of())
                : routeStopRepository.findDirectionByCoursesBatch(withCourse);

        Mono<Map<String, NearestStopResult>> nearestResults = withoutCourse.isEmpty()
                ? Mono.just(Map.of())
                : routeStopRepository.findNearestStopSequencesBatch(withoutCourse);

        return Mono.zip(courseResults, nearestResults)
                .map(tuple -> {
                    Map<String, NearestStopResult> combined = new java.util.HashMap<>(tuple.getT1());
                    combined.putAll(tuple.getT2());
                    return combined;
                });
    }

    @Override
    public Mono<Vehicle> updateVehicleDirection(Vehicle vehicle) {
        if (vehicle == null || !vehicle.hasAssignedRoute() || !vehicle.hasPosition()) {
            return Mono.justOrEmpty(vehicle);
        }

        boolean hasCourse = vehicle.getCourse() != null && vehicle.getCourse() > 0;

        return findNearestStopSequence(vehicle)
                .map(result -> {
                    Vehicle updatedVehicle = vehicle.updateDirection(result.sequence(), result.direction());
                    if (updatedVehicle.getCurrentDirection() != null &&
                            !updatedVehicle.getCurrentDirection().equals(vehicle.getCurrentDirection())) {
                        log.debug("Vehicle {} direction updated: {} (seq: {} -> {}, method: {}, course: {}°)",
                                vehicle.getLicensePlate(),
                                updatedVehicle.getCurrentDirection() == 0 ? "forward" : "backward",
                                vehicle.getLastStopSequence(),
                                result.sequence(),
                                hasCourse ? "course-based" : "nearest-stop",
                                hasCourse ? vehicle.getCourse() : "N/A");
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
                .map(stopResultMap -> {
                    List<Vehicle> result = new ArrayList<>(vehiclesWithoutRoutes);
                    int updated = 0;
                    int skipped = 0;
                    int courseBasedCount = 0;

                    for (Vehicle vehicle : vehiclesWithRoutes) {
                        NearestStopResult stopResult = stopResultMap.get(vehicle.getId().getValue());
                        if (stopResult != null) {
                            Vehicle updatedVehicle = vehicle.updateDirection(stopResult.sequence(), stopResult.direction());
                            result.add(updatedVehicle);
                            updated++;

                            boolean hasCourse = vehicle.getCourse() != null && vehicle.getCourse() > 0;
                            if (hasCourse) courseBasedCount++;

                            if (updatedVehicle.getCurrentDirection() != null &&
                                    !updatedVehicle.getCurrentDirection().equals(vehicle.getCurrentDirection())) {
                                log.debug("Vehicle {} direction: {} (seq: {} -> {}, method: {}, course: {}°)",
                                        vehicle.getLicensePlate(),
                                        updatedVehicle.getCurrentDirection() == 0 ? "forward" : "backward",
                                        vehicle.getLastStopSequence(),
                                        stopResult.sequence(),
                                        hasCourse ? "course" : "nearest",
                                        hasCourse ? String.format("%.1f", vehicle.getCourse()) : "N/A");
                            }
                        } else {
                            result.add(vehicle);
                            skipped++;
                            log.warn("Vehicle {} no stop result found (route={}, pos={},{})",
                                    vehicle.getLicensePlate(),
                                    vehicle.getAssignedRouteId().getValue(),
                                    vehicle.getCurrentLatitude(),
                                    vehicle.getCurrentLongitude());
                        }
                    }

                    if (log.isDebugEnabled()) {
                        log.debug("Direction batch: {} updated ({} course-based), {} skipped",
                                updated, courseBasedCount, skipped);
                    }

                    return result;
                });
    }
}
