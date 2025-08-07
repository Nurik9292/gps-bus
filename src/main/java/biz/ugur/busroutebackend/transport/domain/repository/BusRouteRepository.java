package biz.ugur.busroutebackend.transport.domain.repository;

import biz.ugur.busroutebackend.transport.application.dto.RouteStopDTO;
import biz.ugur.busroutebackend.transport.application.dto.RouteWithGeometryDTO;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BusRouteRepository {

    Mono<BusRoute> save(BusRoute busRoute);

    Mono<BusRoute> findById(BusRouteId routeId);

    Mono<BusRoute> findByRouteNumber(String routeNumber);

    Mono<RouteWithGeometryDTO> findByRouteNumberWithGeometry(String routeNumber);

    Flux<RouteWithGeometryDTO> findAllActiveWithBasicInfo();

    Flux<RouteStopDTO> findRouteStopsOrdered(String routeNumber, Integer direction);

    Flux<RouteInAreaResult> findRoutesIntersectingArea(Double latitude, Double longitude, Integer radiusMeters);

    Mono<RouteVehicleStatistics> getRouteVehicleStatistics(String routeId);

    Flux<BusRoute> findActiveRoutes();

    Mono<Boolean> existsByRouteNumber(String routeNumber);

    Mono<Void> deleteById(BusRouteId routeId);

    Mono<Long> countActiveRoutes();

    Flux<RouteWithGeometryDTO> searchRoutesByNameOrNumber(String query, Integer limit);

    record RouteInAreaResult(String routeId, String routeNumber, String routeName, String routeColor, Integer direction,
                             Double nearestPointLat, Double nearestPointLon, Double distanceToCenterMeters,
                             Long activeVehiclesCount) {
    }

    record RouteVehicleStatistics(Long activeVehiclesCount, Long vehiclesInMotionCount,
                                  Long vehiclesWithRecentPositionCount) {
    }
}