package biz.ugur.busroutebackend.place.domain.repository;

import biz.ugur.busroutebackend.place.domain.model.Place;
import biz.ugur.busroutebackend.place.domain.valueobjects.PlaceId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PlaceRepository extends BaseRepository<Place, PlaceId> {

    Flux<Place> findByCityId(String cityId);

    Flux<Place> findByCityId(String cityId, Pageable pageable);

    Mono<Boolean> existsByNameAndCityId(String name, String cityId);

    Flux<Place> findByCategory(String category, Pageable pageable);

    Mono<Long> countByCityId(String cityId);

    Mono<Long> countByFilters(String cityId, String category, String search);

    Flux<Place> findByFilters(String cityId, String category, String search, Pageable pageable);
}
