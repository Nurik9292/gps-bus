package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionUpdateResult;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
public class UpdateVehiclePositionsUseCase extends BaseUseCase<List<GpsPositionDTO>, VehiclePositionUpdateResult> {

    private final VehicleRepository vehicleRepository;


    public UpdateVehiclePositionsUseCase(VehicleRepository vehicleRepository,
                                         EventBus eventBus,
                                         CorrelationContextService correlationContextService) {
        super(correlationContextService, eventBus);
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    protected Mono<VehiclePositionUpdateResult> process(List<GpsPositionDTO> request) {
        return processInternal(request);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<VehiclePositionUpdateResult> processInternal(List<GpsPositionDTO> gpsPositions) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Processing {} GPS positions from external API - CorrelationId: {}", gpsPositions.size(), correlationId);

            return Flux.fromIterable(gpsPositions)
                    .filter(this::isValidGpsPosition)
                    .flatMap(this::updateVehiclePosition)
                    .collectList()
                    .map(this::createResult)
                    .doOnSuccess(result -> log.info("GPS update completed: {}", result))
                    .doOnError(error -> log.error("GPS update failed", error));
        });
    }

    private Mono<VehicleUpdateStatus> updateVehiclePosition(GpsPositionDTO gpsPosition) {
        return vehicleRepository.findByDeviceId(gpsPosition.getDeviceId())
                .flatMap(vehicle -> updateExistingVehicle(vehicle, gpsPosition))
                .switchIfEmpty(createNewVehicle(gpsPosition))
                .onErrorResume(error -> {
                    log.error("Failed to update vehicle with device ID: {}", gpsPosition.getDeviceId(), error);
                    return Mono.just(VehicleUpdateStatus.failed(gpsPosition.getDeviceId(), error.getMessage()));
                });
    }

    private Mono<VehicleUpdateStatus> updateExistingVehicle(Vehicle vehicle, GpsPositionDTO gpsPosition) {
        try {
            vehicle.updatePosition(
                    gpsPosition.getLatitude(),
                    gpsPosition.getLongitude(),
                    gpsPosition.getSpeed(),
                    gpsPosition.getFixTime(),
                    gpsPosition.getCourse()
            );

            return vehicleRepository.save(vehicle)
                    .map(savedVehicle -> VehicleUpdateStatus.updated(
                            savedVehicle.getId().getValue(),
                            savedVehicle.getDeviceId(),
                            savedVehicle.getLicensePlate()
                    ))
                    .doOnSuccess(status -> log.debug("Updated vehicle position: {}", status));

        } catch (IllegalArgumentException e) {
            log.warn("Invalid GPS data for vehicle {}: {}", vehicle.getLicensePlate(), e.getMessage());
            return Mono.just(VehicleUpdateStatus.invalid(gpsPosition.getDeviceId(), e.getMessage()));
        }
    }

    private Mono<VehicleUpdateStatus> createNewVehicle(GpsPositionDTO gpsPosition) {
        String licensePlate = extractLicensePlateFromGps(gpsPosition);

        if (licensePlate == null) {
            log.warn("Cannot extract license plate from GPS data for device: {}", gpsPosition.getDeviceId());
            return Mono.just(VehicleUpdateStatus.invalid(gpsPosition.getDeviceId(), "Cannot extract license plate"));
        }

        return vehicleRepository.findByLicensePlate(licensePlate)
                .flatMap(existingVehicle -> {
                    log.info("Found existing vehicle by license plate: {}, updating device_id", licensePlate);
                    existingVehicle.setDeviceId(gpsPosition.getDeviceId());
                    return updateExistingVehicle(existingVehicle, gpsPosition);
                })
                .switchIfEmpty(
                        Mono.defer(() -> {
                            try {
                                Vehicle newVehicle = new Vehicle(gpsPosition.getDeviceId(), licensePlate);
                                newVehicle.updatePosition(
                                        gpsPosition.getLatitude(),
                                        gpsPosition.getLongitude(),
                                        gpsPosition.getSpeed(),
                                        gpsPosition.getFixTime(),
                                        gpsPosition.getCourse()
                                );

                                return vehicleRepository.save(newVehicle)
                                        .map(savedVehicle -> VehicleUpdateStatus.created(
                                                savedVehicle.getId().getValue(),
                                                savedVehicle.getDeviceId(),
                                                savedVehicle.getLicensePlate()
                                        ))
                                        .doOnSuccess(status -> log.info("Created new vehicle: {}", status));

                            } catch (IllegalArgumentException e) {
                                log.warn("Invalid data for new vehicle {}: {}", licensePlate, e.getMessage());
                                return Mono.just(VehicleUpdateStatus.invalid(gpsPosition.getDeviceId(), e.getMessage()));
                            }
                        })
                );
    }

    private boolean isValidGpsPosition(GpsPositionDTO gpsPosition) {
        if (gpsPosition == null) return false;
        if (gpsPosition.getDeviceId() == null || gpsPosition.getDeviceId().trim().isEmpty()) return false;
        if (gpsPosition.getLatitude() == null || gpsPosition.getLongitude() == null) return false;

        double lat = gpsPosition.getLatitude();
        double lon = gpsPosition.getLongitude();

        if (lat < 35.0 || lat > 43.0) {
            log.warn("Latitude {} outside Turkmenistan bounds for device {}", lat, gpsPosition.getDeviceId());
            return false;
        }

        if (lon < 52.0 || lon > 67.0) {
            log.warn("Longitude {} outside Turkmenistan bounds for device {}", lon, gpsPosition.getDeviceId());
            return false;
        }

        return true;
    }

    private String extractLicensePlateFromGps(GpsPositionDTO gpsPosition) {
        String name = gpsPosition.getVehicleName();
        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        String normalized = name.trim().toUpperCase().replace("-", "");

        if (normalized.matches("\\d{4}\\s[A-Z]{3}")) {
            return normalized;
        }

        return null;
    }

    private VehiclePositionUpdateResult createResult(List<VehicleUpdateStatus> statuses) {
        long updated = statuses.stream().mapToLong(s -> s.getType() == UpdateType.UPDATED ? 1 : 0).sum();
        long created = statuses.stream().mapToLong(s -> s.getType() == UpdateType.CREATED ? 1 : 0).sum();
        long failed = statuses.stream().mapToLong(s -> s.getType() == UpdateType.FAILED ? 1 : 0).sum();
        long invalid = statuses.stream().mapToLong(s -> s.getType() == UpdateType.INVALID ? 1 : 0).sum();
        long conflicts = statuses.stream().mapToLong(s -> s.getType() == UpdateType.CONFLICT ? 1 : 0).sum();

        return new VehiclePositionUpdateResult(
                updated, created, failed, invalid, conflicts,
                Instant.now(), statuses
        );
    }


    public enum UpdateType {
        UPDATED, CREATED, FAILED, INVALID, CONFLICT
    }

    @Getter
    public static class VehicleUpdateStatus {
        private final UpdateType type;
        private final String deviceId;
        private final String vehicleId;
        private final String licensePlate;
        private final String errorMessage;

        private VehicleUpdateStatus(UpdateType type, String deviceId, String vehicleId,
                                    String licensePlate, String errorMessage) {
            this.type = type;
            this.deviceId = deviceId;
            this.vehicleId = vehicleId;
            this.licensePlate = licensePlate;
            this.errorMessage = errorMessage;
        }

        public static VehicleUpdateStatus updated(String vehicleId, String deviceId, String licensePlate) {
            return new VehicleUpdateStatus(UpdateType.UPDATED, deviceId, vehicleId, licensePlate, null);
        }

        public static VehicleUpdateStatus created(String vehicleId, String deviceId, String licensePlate) {
            return new VehicleUpdateStatus(UpdateType.CREATED, deviceId, vehicleId, licensePlate, null);
        }

        public static VehicleUpdateStatus failed(String deviceId, String errorMessage) {
            return new VehicleUpdateStatus(UpdateType.FAILED, deviceId, null, null, errorMessage);
        }

        public static VehicleUpdateStatus invalid(String deviceId, String errorMessage) {
            return new VehicleUpdateStatus(UpdateType.INVALID, deviceId, null, null, errorMessage);
        }

        public static VehicleUpdateStatus conflict(String deviceId, String errorMessage) {
            return new VehicleUpdateStatus(UpdateType.CONFLICT, deviceId, null, null, errorMessage);
        }

        @Override
        public String toString() {
            return String.format("%s[device=%s, vehicle=%s, plate=%s%s]",
                    type, deviceId, vehicleId, licensePlate,
                    errorMessage != null ? ", error=" + errorMessage : "");
        }
    }
}