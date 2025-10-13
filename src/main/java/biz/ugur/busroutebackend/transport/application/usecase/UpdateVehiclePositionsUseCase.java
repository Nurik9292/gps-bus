package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.geospatial.domain.constants.TurkmenistanBounds;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionUpdateResult;
import biz.ugur.busroutebackend.transport.domain.event.VehiclePositionUpdatedEvent;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
            log.info("Processing {} GPS positions (batch mode) - CorrelationId: {}", gpsPositions.size(), correlationId);

            List<GpsPositionDTO> validPositions = gpsPositions.stream()
                    .filter(this::isValidGpsPosition)
                    .toList();

            if (validPositions.isEmpty()) {
                return Mono.just(new VehiclePositionUpdateResult(0, 0, 0, gpsPositions.size() - validPositions.size(), 0, Instant.now(), List.of()));
            }

            // Extract device IDs for bulk lookup
            List<String> deviceIds = validPositions.stream()
                    .map(GpsPositionDTO::getDeviceId)
                    .distinct()
                    .toList();

            // Batch fetch existing vehicles
            return vehicleRepository.findByDeviceIds(deviceIds)
                    .flatMap(existingVehiclesMap -> processBatch(validPositions, existingVehiclesMap))
                    .doOnSuccess(result -> log.info("GPS batch update completed: {}", result))
                    .doOnError(error -> log.error("GPS batch update failed", error));
        });
    }

    private Mono<VehiclePositionUpdateResult> processBatch(List<GpsPositionDTO> validPositions, Map<String, Vehicle> existingVehicles) {
        List<Vehicle> vehiclesToUpdate = new ArrayList<>();
        List<Vehicle> vehiclesToCreate = new ArrayList<>();
        List<VehicleUpdateStatus> statuses = new ArrayList<>();

        for (GpsPositionDTO gpsPosition : validPositions) {
            try {
                Vehicle vehicle = existingVehicles.get(gpsPosition.getDeviceId());

                if (vehicle != null) {
                    // Update existing vehicle
                    Double oldLatitude = vehicle.getCurrentLatitude();
                    Double oldLongitude = vehicle.getCurrentLongitude();
                    Double oldSpeed = vehicle.getSpeedKmh();

                    vehicle.updatePosition(
                            gpsPosition.getLatitude(),
                            gpsPosition.getLongitude(),
                            gpsPosition.getSpeed(),
                            gpsPosition.getFixTime(),
                            gpsPosition.getCourse()
                    );

                    vehiclesToUpdate.add(vehicle);

                    boolean shouldPublish = shouldPublishPositionEvent(
                            oldLatitude, oldLongitude, oldSpeed,
                            vehicle.getCurrentLatitude(),
                            vehicle.getCurrentLongitude(),
                            vehicle.getSpeedKmh()
                    );

                    if (shouldPublish) {
                        VehiclePositionUpdatedEvent event = new VehiclePositionUpdatedEvent(
                                vehicle.getId().getValue(),
                                vehicle.getDeviceId(),
                                vehicle.getLicensePlate(),
                                vehicle.getRouteNumber(),
                                vehicle.getCurrentLatitude(),
                                vehicle.getCurrentLongitude(),
                                vehicle.getSpeedKmh(),
                                vehicle.getIsInMotion(),
                                vehicle.getLastPositionUpdate(),
                                vehicle.getCourse()
                        );
                        eventBus.publish(event);
                    }

                    statuses.add(VehicleUpdateStatus.updated(
                            vehicle.getId().getValue(),
                            vehicle.getDeviceId(),
                            vehicle.getLicensePlate()
                    ));

                } else {
                    // Create new vehicle
                    String licensePlate = extractLicensePlateFromGps(gpsPosition);
                    if (licensePlate != null) {
                        Vehicle newVehicle = new Vehicle(gpsPosition.getDeviceId(), licensePlate);
                        newVehicle.updatePosition(
                                gpsPosition.getLatitude(),
                                gpsPosition.getLongitude(),
                                gpsPosition.getSpeed(),
                                gpsPosition.getFixTime(),
                                gpsPosition.getCourse()
                        );
                        vehiclesToCreate.add(newVehicle);
                        statuses.add(VehicleUpdateStatus.created(
                                newVehicle.getId().getValue(),
                                newVehicle.getDeviceId(),
                                newVehicle.getLicensePlate()
                        ));
                    } else {
                        statuses.add(VehicleUpdateStatus.invalid(
                                gpsPosition.getDeviceId(),
                                "Cannot extract license plate"
                        ));
                    }
                }
            } catch (IllegalArgumentException e) {
                log.warn("Invalid GPS data for device {}: {}", gpsPosition.getDeviceId(), e.getMessage());
                statuses.add(VehicleUpdateStatus.invalid(gpsPosition.getDeviceId(), e.getMessage()));
            }
        }

        // Batch update and insert
        Mono<Integer> updateMono = vehiclesToUpdate.isEmpty() ?
                Mono.just(0) : vehicleRepository.batchUpdate(vehiclesToUpdate);

        Mono<List<Vehicle>> insertMono = vehiclesToCreate.isEmpty() ?
                Mono.just(List.of()) : vehicleRepository.batchInsert(vehiclesToCreate).collectList();

        return Mono.zip(updateMono, insertMono)
                .map(tuple -> {
                    log.info("Batch operations: {} updated, {} created", tuple.getT1(), tuple.getT2().size());
                    return createResult(statuses);
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
            Double oldLatitude = vehicle.getCurrentLatitude();
            Double oldLongitude = vehicle.getCurrentLongitude();
            Double oldSpeed = vehicle.getSpeedKmh();

            vehicle.updatePosition(
                    gpsPosition.getLatitude(),
                    gpsPosition.getLongitude(),
                    gpsPosition.getSpeed(),
                    gpsPosition.getFixTime(),
                    gpsPosition.getCourse()
            );

            return vehicleRepository.save(vehicle)
                    .doOnSuccess(savedVehicle -> {
                        boolean shouldPublishEvent = shouldPublishPositionEvent(
                                oldLatitude, oldLongitude, oldSpeed,
                                savedVehicle.getCurrentLatitude(),
                                savedVehicle.getCurrentLongitude(),
                                savedVehicle.getSpeedKmh()
                        );

                            VehiclePositionUpdatedEvent event = new VehiclePositionUpdatedEvent(
                                    savedVehicle.getId().getValue(),
                                    savedVehicle.getDeviceId(),
                                    savedVehicle.getLicensePlate(),
                                    savedVehicle.getRouteNumber(),
                                    savedVehicle.getCurrentLatitude(),
                                    savedVehicle.getCurrentLongitude(),
                                    savedVehicle.getSpeedKmh(),
                                    savedVehicle.getIsInMotion(),
                                    savedVehicle.getLastPositionUpdate(),
                                    savedVehicle.getCourse()
                            );

                            eventBus.publish(event);


                    })
                    .map(savedVehicle -> VehicleUpdateStatus.updated(
                            savedVehicle.getId().getValue(),
                            savedVehicle.getDeviceId(),
                            savedVehicle.getLicensePlate()
                    ))
                    .doOnSuccess(status -> log.debug("✅ Vehicle position updated: {}", status));

        } catch (IllegalArgumentException e) {
            log.warn("❌ Invalid GPS data for vehicle {}: {}", vehicle.getLicensePlate(), e.getMessage());
            return Mono.just(VehicleUpdateStatus.invalid(gpsPosition.getDeviceId(), e.getMessage()));
        }
    }

    private boolean shouldPublishPositionEvent(Double oldLat, Double oldLon, Double oldSpeed,
                                               Double newLat, Double newLon, Double newSpeed) {
        if (oldLat == null || oldLon == null) {
            return true;
        }

        double deltaLat = Math.abs(newLat - oldLat);
        double deltaLon = Math.abs(newLon - oldLon);
        boolean positionChanged = deltaLat > 0.00005 || deltaLon > 0.00005;

        boolean speedChanged = false;
        if (oldSpeed != null && newSpeed != null) {
            double speedDelta = Math.abs(newSpeed - oldSpeed);
            speedChanged = speedDelta > 2.0;
        }

        log.trace("Position change check for vehicle: posChanged={}, speedChanged={}, deltaLat={}, deltaLon={}",
                positionChanged, speedChanged, deltaLat, deltaLon);

        return positionChanged || speedChanged;
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
                                        .as(this::persistAndPublish)
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

    /**
     * Validate GPS position data.
     * Now uses centralized TurkmenistanBounds for validation.
     *
     * @since 1.5.0 (Phase 3 - migrated to use TurkmenistanBounds)
     */
    private boolean isValidGpsPosition(GpsPositionDTO gpsPosition) {
        if (gpsPosition == null) return false;
        if (gpsPosition.getDeviceId() == null || gpsPosition.getDeviceId().trim().isEmpty()) return false;
        if (gpsPosition.getLatitude() == null || gpsPosition.getLongitude() == null) return false;

        double lat = gpsPosition.getLatitude();
        double lon = gpsPosition.getLongitude();

        // Use centralized TurkmenistanBounds for validation
        if (!TurkmenistanBounds.isWithinStandardBounds(lat, lon)) {
            log.warn("Coordinates ({}, {}) outside Turkmenistan bounds for device {}",
                lat, lon, gpsPosition.getDeviceId());
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