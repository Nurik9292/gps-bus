package biz.ugur.busroutebackend.transport.domain.service;

import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.RouteStopRepository.NearestStopResult;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public interface VehicleDirectionDetectionService {

    Mono<NearestStopResult> findNearestStopSequence(Vehicle vehicle);

    Mono<Map<String, NearestStopResult>> findNearestStopSequencesBatch(List<Vehicle> vehicles);

    Mono<Vehicle> updateVehicleDirection(Vehicle vehicle);

    Mono<List<Vehicle>> updateVehicleDirectionsBatch(List<Vehicle> vehicles);
}
