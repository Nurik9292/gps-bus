package biz.ugur.busroutebackend.admin.domain.repository;

import biz.ugur.busroutebackend.admin.domain.model.City;
import biz.ugur.busroutebackend.admin.domain.valueobjects.CityId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository interface for City aggregate.
 * Extends BaseRepository for CRUD operations and adds city-specific queries.
 * Supports Specification pattern for flexible, composable queries.
 */
public interface CityRepository extends BaseRepository<City, CityId> {

    // ============= Basic Queries =============

    /**
     * Find all active cities ordered by display order and name.
     *
     * @return Flux of active cities
     */
    Flux<City> findActiveCities();

    /**
     * Check if city with given name exists (case-insensitive).
     *
     * @param name the city name to check
     * @return Mono of Boolean (true if exists)
     */
    Mono<Boolean> existsByName(String name);

    /**
     * Count active cities.
     *
     * @return Mono of Long (count of active cities)
     */
    Mono<Long> countActiveCities();

    /**
     * Check if another city with given name exists (excluding specified ID).
     * Useful for validation when updating a city.
     *
     * @param name the city name to check
     * @param id the city ID to exclude from search
     * @return Mono of Boolean (true if another city exists)
     */
    Mono<Boolean> existsByNameAndIdNot(String name, CityId id);

    // ============= Specification Pattern Support =============

    /**
     * Find cities matching the given specification.
     *
     * @param specification the specification to match
     * @return Flux of cities matching the specification
     */
    Flux<City> findBySpecification(Specification<City> specification);

    /**
     * Find cities matching the given specification with pagination.
     *
     * @param specification the specification to match
     * @param pageable pagination parameters
     * @return Flux of cities matching the specification
     */
    Flux<City> findBySpecification(Specification<City> specification, Pageable pageable);

    /**
     * Count cities matching the given specification.
     *
     * @param specification the specification to match
     * @return Mono of Long (count of matching cities)
     */
    Mono<Long> countBySpecification(Specification<City> specification);
}

