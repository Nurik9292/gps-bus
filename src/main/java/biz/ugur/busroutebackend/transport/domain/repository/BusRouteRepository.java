package biz.ugur.busroutebackend.transport.domain.repository;

import biz.ugur.busroutebackend.shared.base.BaseRepository;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteInAreaInfo;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteVehicleStatistics;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface BusRouteRepository extends BaseRepository<BusRoute, BusRouteId> {


    Mono<BusRoute> findByRouteNumber(String routeNumber);

    Flux<BusRoute> findActiveRoutes();

    Mono<Boolean> existsByRouteNumber(String routeNumber);

    Mono<Long> countActiveRoutes();

    Flux<RouteStopInfo> getRouteStopsInfo(BusRouteId routeId);

    Flux<RouteStopInfo> getRouteStopsInfoByNumber(String routeNumber, Integer direction);

    Mono<RouteVehicleStatistics> getRouteVehicleStatistics(BusRouteId routeId);

    Flux<BusRoute> searchRoutesByNameOrNumber(String query, Integer limit);

    Flux<RouteInAreaInfo> findRoutesIntersectingArea(Double latitude, Double longitude, Integer radiusMeters);


    Flux<BusRoute> findBySpecification(Specification<BusRoute> specification);


    Flux<BusRoute> findBySpecification(Specification<BusRoute> specification, Pageable pageable);


    Mono<Long> countBySpecification(Specification<BusRoute> specification);

    Flux<BusRoute> searchWithRelevance(String query, Boolean isActive, Pageable pageable);

    Mono<Long> countBySearch(String query, Boolean isActive);
}