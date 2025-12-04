package biz.ugur.busroutebackend.client.domain.repository;

import biz.ugur.busroutebackend.client.domain.model.RouteFavorite;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.domain.valueobject.RouteFavoriteId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RouteFavoriteRepository extends BaseRepository<RouteFavorite, RouteFavoriteId> {

    Flux<RouteFavorite> findByClientId(ClientId clientId);

    Mono<Boolean> existsByClientIdAndRouteId(ClientId clientId, BusRouteId routeId);

    Mono<Void> deleteByClientIdAndRouteId(ClientId clientId, BusRouteId routeId);

    Flux<RouteFavorite> findBySpecification(Specification<RouteFavorite> specification);

    Flux<RouteFavorite> findBySpecification(Specification<RouteFavorite> specification, Pageable pageable);

    Mono<Long> countBySpecification(Specification<RouteFavorite> specification);
}