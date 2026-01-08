package biz.ugur.busroutebackend.geospatial.application.usecase;

import biz.ugur.busroutebackend.geospatial.domain.model.Garage;
import biz.ugur.busroutebackend.geospatial.domain.repository.GarageRepository;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Optional;

@Service
@Slf4j
public class DetectGarageTransitionsUseCase {

    private final GarageRepository garageRepository;

    @Value("${business.garage.entry-detection.speed-threshold-kmh:2.0}")
    private Double entrySpeedThreshold;

    @Value("${business.garage.exit-detection.min-distance-meters:50}")
    private Integer exitDistanceBuffer;

    @Value("${business.garage.exit-detection.min-speed-kmh:5.0}")
    private Double exitSpeedThreshold;

    public DetectGarageTransitionsUseCase(GarageRepository garageRepository) {
        this.garageRepository = garageRepository;
    }


    public Mono<Optional<Garage>> findGarageAtPosition(Coordinates position) {
        return garageRepository.findGarageContainingPosition(position)
                .doOnNext(result -> {
                    result.ifPresent(garage -> log.debug("Vehicle at ({}, {}) is inside garage: {}",
                            position.getLatitude(), position.getLongitude(),
                            garage.getName()));
                });
    }


    public Mono<Boolean> isVehicleEntryCandidate(Coordinates position, Double speedKmh) {
        if (speedKmh == null || speedKmh >= entrySpeedThreshold) {
            return Mono.just(false);
        }

        return findGarageAtPosition(position)
                .map(Optional::isPresent);
    }

    public Mono<Boolean> hasVehicleExitedGarage(Garage garage, Coordinates position, Double speedKmh) {
        if (garage == null) {
            return Mono.just(false);
        }

        if (speedKmh != null && speedKmh < exitSpeedThreshold) {
            return Mono.just(false);
        }

        return garageRepository.hasPositionExitedGarage(garage.getId(), position, exitDistanceBuffer)
                .doOnNext(hasExited -> {
                    if (hasExited) {
                        log.debug("Vehicle has exited garage {} (using {} check)",
                                garage.getName(),
                                garage.hasBoundary() ? "polygon" : "radius");
                    }
                });
    }

    public Flux<Garage> getAllActiveGarages() {
        return garageRepository.findAllActive()
                .doOnNext(garage -> log.debug("Active garage: {} at ({}, {})",
                        garage.getName(),
                        garage.getLocation().getLatitude(),
                        garage.getLocation().getLongitude()));
    }
}
