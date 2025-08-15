package biz.ugur.busroutebackend.transport.domain.repository;

import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteInAreaInfo;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteVehicleStatistics;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface BusRouteRepository {

    Mono<BusRoute> save(BusRoute busRoute);

    Mono<BusRoute> findById(BusRouteId routeId);

    Mono<BusRoute> findByRouteNumber(String routeNumber);

    Flux<BusRoute> findActiveRoutes();

    Flux<BusRoute> getRoutesWithPagination(Pageable pageable);

    Mono<Boolean> existsByRouteNumber(String routeNumber);

    Mono<Void> deleteById(BusRouteId routeId);

    Mono<Long> countActiveRoutes();




    Flux<RouteStopInfo> getRouteStopsInfo(BusRouteId routeId);

    Flux<RouteStopInfo> getRouteStopsInfoByNumber(String routeNumber, Integer direction);

    Mono<RouteVehicleStatistics> getRouteVehicleStatistics(BusRouteId routeId);

    Flux<BusRoute> searchRoutesByNameOrNumber(String query, Integer limit);

    Flux<RouteInAreaInfo> findRoutesIntersectingArea(Double latitude, Double longitude, Integer radiusMeters);
}