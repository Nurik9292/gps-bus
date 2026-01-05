package biz.ugur.busroutebackend.transport.domain.repository;

import biz.ugur.busroutebackend.transport.domain.valueobject.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;


public interface RouteStopRepository {

    Mono<Void> deleteExistingStops(String routeId);

    Mono<Void> insertRouteStop(String routeId, String stopId, int sequence, int direction);

    Flux<RouteStopInfo> getRouteStops(String routeId, int direction);

    Flux<RouteStopDetail> getRouteStopsDetail(String routeId, int direction);

    Flux<StopRouteDetail> getStopRoutesDetail(String stopId, int direction);

    Mono<List<RouteStopDetail>> getForwardStopsDetail(String routeId);

    Mono<List<RouteStopDetail>> getBackwardStopsDetail(String routeId);

    Mono<RouteStopsData> getAllRouteStopsData(String routeId);

    Mono<RouteStopsStatistics> getRouteStopsStatistics(String routeId);

    Mono<NearestStopResult> findNearestStopSequence(String routeId, Double latitude, Double longitude, Integer currentDirection);

    Mono<Map<String, NearestStopResult>> findNearestStopSequencesBatch(
            java.util.List<VehiclePositionKey> vehiclePositions);

    record VehiclePositionKey(String vehicleId, String routeId, Double latitude, Double longitude, Integer currentDirection, Double course) {}

    record NearestStopResult(Integer sequence, Integer direction) {}

    Mono<NearestStopResult> findDirectionByCourse(String routeId, Double latitude, Double longitude, Double course);

    Mono<Map<String, NearestStopResult>> findDirectionByCoursesBatch(List<VehiclePositionKey> vehiclePositions);
}
