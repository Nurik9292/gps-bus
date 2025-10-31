package biz.ugur.busroutebackend.transport.application.factory;

import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.service.LicensePlateExtractor;
import biz.ugur.busroutebackend.transport.domain.service.VehicleValidationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Optional;


@Component
@Slf4j
public class VehicleFactory {

    private final VehicleRepository vehicleRepository;
    private final LicensePlateExtractor licensePlateExtractor;
    private final VehicleValidationService validationService;

    public VehicleFactory(VehicleRepository vehicleRepository,
                          LicensePlateExtractor licensePlateExtractor,
                          VehicleValidationService validationService) {
        this.vehicleRepository = vehicleRepository;
        this.licensePlateExtractor = licensePlateExtractor;
        this.validationService = validationService;
    }


    public Mono<Vehicle> createOrFindFromGpsData(GpsPositionDTO gpsPosition) {
        if (!validationService.isValidGpsPosition(gpsPosition)) {
            return Mono.error(new IllegalArgumentException(
                    "Invalid GPS position data for device: " + gpsPosition.getDeviceId()));
        }

        return vehicleRepository.findByDeviceId(gpsPosition.getDeviceId())
                .switchIfEmpty(Mono.defer(() -> findByLicensePlateOrCreate(gpsPosition)));
    }

    public Vehicle updateVehiclePosition(Vehicle vehicle, GpsPositionDTO gpsPosition) {
        return vehicle.updatePosition(
                gpsPosition.getLatitude(),
                gpsPosition.getLongitude(),
                gpsPosition.getSpeed(),
                gpsPosition.getFixTime(),
                gpsPosition.getCourse()
        );
    }


    public Mono<Vehicle> createNewVehicle(GpsPositionDTO gpsPosition) {
        Optional<String> licensePlateOpt = licensePlateExtractor.extractFromGpsData(gpsPosition);

        if (licensePlateOpt.isEmpty()) {
            return Mono.error(new IllegalArgumentException(
                    "Cannot extract license plate from GPS data for device: " + gpsPosition.getDeviceId()));
        }

        String licensePlate = licensePlateOpt.get();

        try {
            Vehicle newVehicle = Vehicle.create(gpsPosition.getDeviceId(), licensePlate);
            Vehicle positionedVehicle = updateVehiclePosition(newVehicle, gpsPosition);

            log.info("Created new vehicle: deviceId={}, licensePlate={}",
                    gpsPosition.getDeviceId(), licensePlate);

            return Mono.just(positionedVehicle);

        } catch (IllegalArgumentException e) {
            log.error("Failed to create vehicle from GPS data: {}", e.getMessage());
            return Mono.error(e);
        }
    }

    private Mono<Vehicle> findByLicensePlateOrCreate(GpsPositionDTO gpsPosition) {
        Optional<String> licensePlateOpt = licensePlateExtractor.extractFromGpsData(gpsPosition);

        if (licensePlateOpt.isEmpty()) {
            log.warn("Cannot extract license plate for device: {}", gpsPosition.getDeviceId());
            return Mono.empty();
        }

        String licensePlate = licensePlateOpt.get();

        return vehicleRepository.findByLicensePlate(licensePlate)
                .flatMap(existingVehicle -> {
                    log.info("Found existing vehicle by license plate: {}, updating deviceId",
                            licensePlate);
                    Vehicle updated = existingVehicle.updateDeviceId(gpsPosition.getDeviceId());
                    return Mono.just(updated);
                })
                .switchIfEmpty(Mono.defer(() -> createNewVehicle(gpsPosition)));
    }
}
