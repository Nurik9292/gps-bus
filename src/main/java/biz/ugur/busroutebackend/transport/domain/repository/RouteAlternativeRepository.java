package biz.ugur.busroutebackend.transport.domain.repository;

import biz.ugur.busroutebackend.shared.base.BaseRepository;
import biz.ugur.busroutebackend.transport.domain.model.RouteAlternative;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteAlternativeId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository interface for RouteAlternative entities.
 */
public interface RouteAlternativeRepository extends BaseRepository<RouteAlternative, RouteAlternativeId> {

    /**
     * Find all alternatives for a primary route, ordered by priority.
     */
    Flux<RouteAlternative> findByPrimaryRouteId(BusRouteId primaryRouteId);

    /**
     * Find all routes that use this route as an alternative.
     */
    Flux<RouteAlternative> findByAlternativeRouteId(BusRouteId alternativeRouteId);

    /**
     * Check if a specific alternative link exists.
     */
    Mono<Boolean> existsByPrimaryAndAlternative(BusRouteId primaryRouteId, BusRouteId alternativeRouteId);

    /**
     * Find a specific alternative link by primary and alternative route IDs.
     */
    Mono<RouteAlternative> findByPrimaryAndAlternative(BusRouteId primaryRouteId, BusRouteId alternativeRouteId);

    /**
     * Delete all alternatives for a primary route.
     */
    Mono<Void> deleteByPrimaryRouteId(BusRouteId primaryRouteId);

    /**
     * Delete all alternatives that reference a specific alternative route.
     */
    Mono<Void> deleteByAlternativeRouteId(BusRouteId alternativeRouteId);

    /**
     * Count alternatives for a primary route.
     */
    Mono<Long> countByPrimaryRouteId(BusRouteId primaryRouteId);
}
