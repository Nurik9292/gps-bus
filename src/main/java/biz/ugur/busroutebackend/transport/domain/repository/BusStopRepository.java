package biz.ugur.busroutebackend.transport.domain.repository;

import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface BusStopRepository {

    Mono<BusStop> save(BusStop busStop);

    Mono<BusStop> findById(BusStopId stopId);

    Flux<BusStop> findByStopName(String stopName);

    Flux<BusStop> findStopsWithinRadius(Double centerLat, Double centerLon, Double radiusKm);

    Flux<BusStop> findByRouteId(String routeId);

    Flux<BusStop> findActiveStops();

    Mono<Boolean> existsByStopCode(String stopCode);

    Mono<Void> deleteById(BusStopId stopId);

    Mono<Long> countActiveStops();
}