package biz.ugur.busroutebackend.client.domain.repository;

import biz.ugur.busroutebackend.client.domain.model.RouteFavorite;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.domain.valueobject.RouteFavoriteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RouteFavoriteRepository {

    Mono<RouteFavorite> save(RouteFavorite routeFavorite);

    Flux<RouteFavorite> findByClientId(ClientId clientId);

    Mono<Boolean> existsByClientIdAndRouteId(ClientId clientId, BusRouteId routeId);

    Mono<Void> deleteByClientIdAndRouteId(ClientId clientId, BusRouteId routeId);

    Mono<Void> deleteById(RouteFavoriteId id);
}