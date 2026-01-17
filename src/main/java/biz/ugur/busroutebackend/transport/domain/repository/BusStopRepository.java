package biz.ugur.busroutebackend.transport.domain.repository;

import biz.ugur.busroutebackend.shared.base.BaseRepository;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.transport.application.dto.BusArrivalInfo;
import biz.ugur.busroutebackend.transport.domain.valueobject.StopWithRouteDistance;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BusStopRepository extends BaseRepository<BusStop, BusStopId> {

    Flux<BusStop> findByStopName(String stopName);

    Flux<BusStop> findStopsWithinRadius(Double centerLat, Double centerLon, Double radiusKm);

    Flux<BusStop> findByRouteId(String routeId);

    Flux<BusStop> findActiveStops();

    Mono<Boolean> existsByStopCode(String stopCode);

    Mono<Long> countActiveStops();

    Flux<BusStop> searchByName(String query, Integer limit);

    Mono<Boolean> existsByStopName(String stopName);

    Flux<BusStop> findBySpecification(Specification<BusStop> specification);

    Flux<BusStop> findBySpecification(Specification<BusStop> specification, Pageable pageable);

    Mono<Long> countBySpecification(Specification<BusStop> specification);

    Flux<BusArrivalInfo> findArrivingVehicles(BusStopId stopId, Double stopLatitude, Double stopLongitude);


    Flux<BusStop> findStopsOnRouteAhead(String routeNumber, Double vehicleLat, Double vehicleLon, int maxStops);

    Flux<StopWithRouteDistance> findStopsOnRouteAheadWithRouteDistance(
            String routeNumber,
            Double vehicleLat,
            Double vehicleLon,
            Integer direction,
            int maxStops
    );
}