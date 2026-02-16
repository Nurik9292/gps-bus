package biz.ugur.busroutebackend.place.domain.repository;

import biz.ugur.busroutebackend.place.domain.model.Street;
import biz.ugur.busroutebackend.place.domain.valueobjects.StreetId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface StreetRepository extends BaseRepository<Street, StreetId> {

    Flux<Street> findByCityId(String cityId);

    Flux<Street> findByCityId(String cityId, Pageable pageable);

    Mono<Long> countByCityId(String cityId);

    Mono<Boolean> existsByNameAndCityId(String name, String cityId);
}
