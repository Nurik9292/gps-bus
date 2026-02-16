package biz.ugur.busroutebackend.place.domain.repository;

import biz.ugur.busroutebackend.place.domain.model.StreetAlias;
import biz.ugur.busroutebackend.place.domain.valueobjects.StreetAliasId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface StreetAliasRepository extends BaseRepository<StreetAlias, StreetAliasId> {

    Flux<StreetAlias> findByStreetId(String streetId);

    Mono<Void> deleteByStreetId(String streetId);

    Flux<StreetAlias> findAllWithStreetNames();
}
