package biz.ugur.busroutebackend.place.domain.repository;

import biz.ugur.busroutebackend.place.domain.model.PlaceAlias;
import biz.ugur.busroutebackend.place.domain.valueobjects.PlaceAliasId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PlaceAliasRepository extends BaseRepository<PlaceAlias, PlaceAliasId> {

    Flux<PlaceAlias> findByPlaceId(String placeId);

    Mono<Void> deleteByPlaceId(String placeId);
}
