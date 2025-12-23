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

/**
 * Use case for detecting garage entry and exit events for vehicles.
 *
 * Business Rules:
 * - Entry detection: Vehicle must be stationary (speed < threshold) inside geofence for X minutes
 * - Exit detection: Vehicle must move beyond geofence radius + buffer with speed > threshold
 */
@Service
@Slf4j
public class DetectGarageTransitionsUseCase {

    private final GarageRepository garageRepository;

    // Configuration from application.yml: business.garage.entry-detection
    @Value("${business.garage.entry-detection.min-idle-time-minutes:10}")
    private Integer entryIdleTimeMinutes;

    @Value("${business.garage.entry-detection.speed-threshold-kmh:2.0}")
    private Double entrySpeedThreshold;

    // Configuration from application.yml: business.garage.exit-detection
    @Value("${business.garage.exit-detection.min-distance-meters:50}")
    private Integer exitDistanceBuffer;

    @Value("${business.garage.exit-detection.min-speed-kmh:5.0}")
    private Double exitSpeedThreshold;

    public DetectGarageTransitionsUseCase(GarageRepository garageRepository) {
        this.garageRepository = garageRepository;
    }

    /**
     * Find the garage that contains the given coordinates
     *
     * @param position Vehicle position
     * @return Garage if vehicle is inside any garage geofence, empty otherwise
     */
    public Mono<Optional<Garage>> findGarageAtPosition(Coordinates position) {
        return garageRepository.findAllActive()
                .filter(garage -> garage.isVehicleInside(position))
                .next()
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .doOnNext(result -> {
                    if (result.isPresent()) {
                        log.debug("Vehicle at ({}, {}) is inside garage: {}",
                                position.getLatitude(), position.getLongitude(),
                                result.get().getName());
                    }
                });
    }

    /**
     * Check if vehicle should be marked as "entered garage"
     *
     * Criteria:
     * - Vehicle is inside garage geofence
     * - Vehicle speed is below threshold (stationary)
     *
     * @param position Vehicle position
     * @param speedKmh Vehicle speed in km/h
     * @return true if vehicle meets entry criteria
     */
    public Mono<Boolean> isVehicleEntryCandidate(Coordinates position, Double speedKmh) {
        if (speedKmh == null || speedKmh >= entrySpeedThreshold) {
            return Mono.just(false);
        }

        return findGarageAtPosition(position)
                .map(Optional::isPresent);
    }

    /**
     * Check if vehicle has exited the garage
     *
     * Criteria:
     * - Vehicle is beyond garage radius + buffer distance
     * - Vehicle speed is above threshold (moving)
     *
     * @param garage The garage to check against
     * @param position Current vehicle position
     * @param speedKmh Current vehicle speed
     * @return true if vehicle has exited
     */
    public boolean hasVehicleExitedGarage(Garage garage, Coordinates position, Double speedKmh) {
        if (garage == null) {
            return false;
        }

        if (speedKmh != null && speedKmh < exitSpeedThreshold) {
            // Vehicle not moving fast enough to be considered "exited"
            return false;
        }

        boolean beyondGeofence = garage.hasVehicleExited(position, exitDistanceBuffer);

        if (beyondGeofence) {
            log.debug("Vehicle has exited garage {} - distance from center: {} meters",
                    garage.getName(),
                    garage.calculateDistanceMeters(position));
        }

        return beyondGeofence;
    }

    /**
     * Get all active garages
     *
     * @return Flux of active garages
     */
    public Flux<Garage> getAllActiveGarages() {
        return garageRepository.findAllActive()
                .doOnNext(garage -> log.debug("Active garage: {} at ({}, {}) radius: {}m",
                        garage.getName(),
                        garage.getLocation().getLatitude(),
                        garage.getLocation().getLongitude(),
                        garage.getRadiusMeters()));
    }

    /**
     * Calculate distance from vehicle to garage center
     *
     * @param garage The garage
     * @param position Vehicle position
     * @return Distance in meters
     */
    public double calculateDistanceToGarage(Garage garage, Coordinates position) {
        return garage.calculateDistanceMeters(position);
    }

    /**
     * Get entry speed threshold (for determining if vehicle is stationary)
     */
    public Double getEntrySpeedThreshold() {
        return entrySpeedThreshold;
    }

    /**
     * Get minimum idle time in minutes for garage entry
     */
    public Integer getEntryIdleTimeMinutes() {
        return entryIdleTimeMinutes;
    }

    /**
     * Get exit speed threshold
     */
    public Double getExitSpeedThreshold() {
        return exitSpeedThreshold;
    }

    /**
     * Get exit distance buffer
     */
    public Integer getExitDistanceBuffer() {
        return exitDistanceBuffer;
    }
}
