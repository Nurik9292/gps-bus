package biz.ugur.busroutebackend.client.domain.repository;

import biz.ugur.busroutebackend.client.domain.model.StopFavorite;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.domain.valueobject.StopFavoriteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface StopFavoriteRepository {

    Mono<StopFavorite> save(StopFavorite stopFavorite);

    Flux<StopFavorite> findByClientId(ClientId clientId);

    Mono<Boolean> existsByClientIdAndStopId(ClientId clientId, BusStopId stopId);

    Mono<Void> deleteByClientIdAndStopId(ClientId clientId, BusStopId stopId);

    Mono<Void> deleteById(StopFavoriteId id);
}