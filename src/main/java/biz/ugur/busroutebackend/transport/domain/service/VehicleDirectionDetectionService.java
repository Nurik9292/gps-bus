package biz.ugur.busroutebackend.transport.domain.service;

import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public interface VehicleDirectionDetectionService {

    Mono<Integer> findNearestStopSequence(Vehicle vehicle);

    Mono<Map<String, Integer>> findNearestStopSequencesBatch(List<Vehicle> vehicles);

    Mono<Vehicle> updateVehicleDirection(Vehicle vehicle);

    Mono<List<Vehicle>> updateVehicleDirectionsBatch(List<Vehicle> vehicles);
}
