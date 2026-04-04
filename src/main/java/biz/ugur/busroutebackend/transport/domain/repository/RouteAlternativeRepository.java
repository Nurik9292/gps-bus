package biz.ugur.busroutebackend.transport.domain.repository;

import biz.ugur.busroutebackend.shared.base.BaseRepository;
import biz.ugur.busroutebackend.transport.domain.model.RouteAlternative;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteAlternativeId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RouteAlternativeRepository extends BaseRepository<RouteAlternative, RouteAlternativeId> {

    Flux<RouteAlternative> findByPrimaryRouteId(BusRouteId primaryRouteId);

    Flux<RouteAlternative> findByAlternativeRouteId(BusRouteId alternativeRouteId);

    Mono<Boolean> existsByPrimaryAndAlternative(BusRouteId primaryRouteId, BusRouteId alternativeRouteId);

    Mono<RouteAlternative> findByPrimaryAndAlternative(BusRouteId primaryRouteId, BusRouteId alternativeRouteId);

    Mono<Void> deleteByPrimaryRouteId(BusRouteId primaryRouteId);

    Mono<Void> deleteByAlternativeRouteId(BusRouteId alternativeRouteId);

    Mono<Long> countByPrimaryRouteId(BusRouteId primaryRouteId);
}
