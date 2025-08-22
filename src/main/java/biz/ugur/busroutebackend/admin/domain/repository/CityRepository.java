package biz.ugur.busroutebackend.admin.domain.repository;

import biz.ugur.busroutebackend.admin.domain.model.City;
import biz.ugur.busroutebackend.admin.domain.valueobjects.CityId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CityRepository extends BaseRepository<City, CityId> {

    Flux<City> findActiveCities();

    Mono<Boolean> existsByName(String name);

    Mono<Long> countActiveCities();

    Mono<Boolean> existsByNameAndIdNot(String name, CityId id);

}

