package biz.ugur.busroutebackend.admin.domain.repository;

import biz.ugur.busroutebackend.admin.domain.model.City;
import biz.ugur.busroutebackend.admin.domain.valueobjects.CityId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CityRepository {

    Mono<City> save(City city);

    Mono<City> findById(CityId cityId);

    Flux<City> findActiveCities();

    Flux<City> findAllCities();

    Mono<Boolean> existsByName(String name);

    Mono<Void> deleteById(CityId cityId);

    Mono<Long> countActiveCities();
}

