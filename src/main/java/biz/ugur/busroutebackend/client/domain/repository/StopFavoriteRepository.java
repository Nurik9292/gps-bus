package biz.ugur.busroutebackend.client.domain.repository;

import biz.ugur.busroutebackend.client.domain.model.StopFavorite;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.domain.valueobject.StopFavoriteId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface StopFavoriteRepository extends BaseRepository<StopFavorite, StopFavoriteId> {

    Flux<StopFavorite> findByClientId(ClientId clientId);

    Mono<Boolean> existsByClientIdAndStopId(ClientId clientId, BusStopId stopId);

    Mono<Void> deleteByClientIdAndStopId(ClientId clientId, BusStopId stopId);

    Flux<StopFavorite> findBySpecification(Specification<StopFavorite> specification);

    Flux<StopFavorite> findBySpecification(Specification<StopFavorite> specification, Pageable pageable);

    Mono<Long> countBySpecification(Specification<StopFavorite> specification);
}