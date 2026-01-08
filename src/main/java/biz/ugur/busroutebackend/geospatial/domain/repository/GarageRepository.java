package biz.ugur.busroutebackend.geospatial.domain.repository;

import biz.ugur.busroutebackend.geospatial.domain.model.Garage;
import biz.ugur.busroutebackend.geospatial.domain.valueobject.GarageId;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;


public interface GarageRepository {

    Mono<Garage> save(Garage garage);

    Mono<Garage> findById(GarageId id);

    Flux<Garage> findAllActive();

    Flux<Garage> findAll();

    Flux<Garage> findByCityId(String cityId);

    Mono<Void> deleteById(GarageId id);

    Mono<Optional<Garage>> findGarageContainingPosition(Coordinates position);

    Mono<Boolean> isPositionInsideGarage(GarageId garageId, Coordinates position);

    Mono<Boolean> hasPositionExitedGarage(GarageId garageId, Coordinates position, int bufferMeters);
}
