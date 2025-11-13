package biz.ugur.busroutebackend.admin.domain.repository;

import biz.ugur.busroutebackend.admin.domain.model.City;
import biz.ugur.busroutebackend.admin.domain.valueobjects.CityId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CityRepository extends BaseRepository<City, CityId> {


    Flux<City> findActiveCities();

    Mono<Boolean> existsByName(String name);

    Mono<Long> countActiveCities();

    Mono<Boolean> existsByNameAndIdNot(String name, CityId id);


    Flux<City> findBySpecification(Specification<City> specification);

    Flux<City> findBySpecification(Specification<City> specification, Pageable pageable);

    Mono<Long> countBySpecification(Specification<City> specification);
}

