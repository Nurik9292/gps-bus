package biz.ugur.busroutebackend.geospatial.domain.repository;

import biz.ugur.busroutebackend.geospatial.domain.model.Garage;
import biz.ugur.busroutebackend.geospatial.domain.valueobject.GarageId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository interface for Garage aggregate
 */
public interface GarageRepository {

    /**
     * Save a garage
     *
     * @param garage the garage to save
     * @return saved garage
     */
    Mono<Garage> save(Garage garage);

    /**
     * Find garage by ID
     *
     * @param id garage ID
     * @return garage if found
     */
    Mono<Garage> findById(GarageId id);

    /**
     * Find all active garages
     *
     * @return flux of active garages
     */
    Flux<Garage> findAllActive();

    /**
     * Find all garages
     *
     * @return flux of all garages
     */
    Flux<Garage> findAll();

    /**
     * Find garages by city ID
     *
     * @param cityId city ID
     * @return flux of garages in the city
     */
    Flux<Garage> findByCityId(String cityId);

    /**
     * Delete garage by ID
     *
     * @param id garage ID
     * @return void
     */
    Mono<Void> deleteById(GarageId id);
}
