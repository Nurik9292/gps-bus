package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.geospatial.application.usecase.DetectGarageTransitionsUseCase;
import biz.ugur.busroutebackend.geospatial.application.usecase.ProcessGarageEntryUseCase;
import biz.ugur.busroutebackend.geospatial.application.usecase.ProcessGarageExitUseCase;
import biz.ugur.busroutebackend.geospatial.domain.model.Garage;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionUpdateResult;
import biz.ugur.busroutebackend.transport.application.factory.VehicleFactory;
import biz.ugur.busroutebackend.transport.domain.event.VehiclePositionUpdatedEvent;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.service.LicensePlateExtractor;
import biz.ugur.busroutebackend.transport.domain.service.PositionChangeDetector;
import biz.ugur.busroutebackend.transport.domain.service.VehicleDirectionDetectionService;
import biz.ugur.busroutebackend.transport.domain.service.VehicleValidationService;
import biz.ugur.busroutebackend.transport.application.usecase.assignment.ProcessExpiredAssignmentsUseCase;
import biz.ugur.busroutebackend.transport.infrastructure.redis.VehicleGpsHistoryService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;

import java.time.LocalDateTime;

@Service
@Slf4j
public class UpdateVehiclePositionsUseCase extends BaseUseCase<List<GpsPositionDTO>, VehiclePositionUpdateResult> {

    private final VehicleRepository vehicleRepository;
    private final VehicleFactory vehicleFactory;
    private final VehicleValidationService validationService;
    private final PositionChangeDetector positionChangeDetector;
    private final LicensePlateExtractor licensePlateExtractor;
    private final VehicleDirectionDetectionService directionDetectionService;
    private final DetectGarageTransitionsUseCase garageTransitionsUseCase;
    private final ProcessGarageEntryUseCase processGarageEntryUseCase;
    private final ProcessGarageExitUseCase processGarageExitUseCase;
    private final VehicleGpsHistoryService gpsHistoryService;
    private final ProcessExpiredAssignmentsUseCase processExpiredAssignmentsUseCase;

    public UpdateVehiclePositionsUseCase(VehicleRepository vehicleRepository,
                                         VehicleFactory vehicleFactory,
                                         VehicleValidationService validationService,
                                         PositionChangeDetector positionChangeDetector,
                                         LicensePlateExtractor licensePlateExtractor,
                                         VehicleDirectionDetectionService directionDetectionService,
                                         DetectGarageTransitionsUseCase garageTransitionsUseCase,
                                         ProcessGarageEntryUseCase processGarageEntryUseCase,
                                         ProcessGarageExitUseCase processGarageExitUseCase,
                                         VehicleGpsHistoryService gpsHistoryService,
                                         ProcessExpiredAssignmentsUseCase processExpiredAssignmentsUseCase,
                                         EventBus eventBus,
                                         CorrelationContextService correlationContextService) {
        super(correlationContextService, eventBus);
        this.vehicleRepository = vehicleRepository;
        this.vehicleFactory = vehicleFactory;
        this.validationService = validationService;
        this.positionChangeDetector = positionChangeDetector;
        this.licensePlateExtractor = licensePlateExtractor;
        this.directionDetectionService = directionDetectionService;
        this.garageTransitionsUseCase = garageTransitionsUseCase;
        this.processGarageEntryUseCase = processGarageEntryUseCase;
        this.processGarageExitUseCase = processGarageExitUseCase;
        this.gpsHistoryService = gpsHistoryService;
        this.processExpiredAssignmentsUseCase = processExpiredAssignmentsUseCase;
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
                    .filter(validationService::isValidGpsPosition)
                    .toList();

            if (validPositions.isEmpty()) {
                return Mono.just(new VehiclePositionUpdateResult(0, 0, 0, gpsPositions.size() - validPositions.size(), 0, LocalDateTime.now(), List.of()));
            }

            List<String> deviceIds = validPositions.stream()
                    .map(GpsPositionDTO::getDeviceId)
                    .filter(deviceId -> deviceId != null && !deviceId.isBlank())
                    .distinct()
                    .toList();

            if (deviceIds.isEmpty()) {
                log.warn("No valid device IDs found after filtering {} positions", validPositions.size());
                return Mono.just(new VehiclePositionUpdateResult(0, 0, 0, validPositions.size(), 0, LocalDateTime.now(), List.of()));
            }

            return vehicleRepository.findByDeviceIds(deviceIds)
                    .flatMap(existingVehiclesMap -> processBatch(validPositions, existingVehiclesMap))
                    .doOnSuccess(result -> log.info("GPS batch update completed: {}", result.updatedCount()))
                    .doOnError(error -> log.error("GPS batch update failed", error));
        });
    }

    private Mono<VehiclePositionUpdateResult> processBatch(List<GpsPositionDTO> validPositions, Map<String, Vehicle> existingVehicles) {
        // Deduplicate GPS positions by device ID, keeping the latest timestamp
        Map<String, GpsPositionDTO> latestPositionsByDevice = new java.util.LinkedHashMap<>();
        for (GpsPositionDTO position : validPositions) {
            String deviceId = position.getDeviceId();
            GpsPositionDTO existing = latestPositionsByDevice.get(deviceId);

            if (existing == null || position.getFixTime().isAfter(existing.getFixTime())) {
                latestPositionsByDevice.put(deviceId, position);
            }
        }

        log.debug("Batched {} updates into {} unique vehicles",
                validPositions.size(), latestPositionsByDevice.size());

        List<Vehicle> vehiclesToUpdate = new ArrayList<>();
        List<Vehicle> vehiclesToCreate = new ArrayList<>();
        List<VehicleUpdateStatus> statuses = new ArrayList<>();

        for (GpsPositionDTO gpsPosition : latestPositionsByDevice.values()) {
            try {
                Vehicle vehicle = existingVehicles.get(gpsPosition.getDeviceId());

                if (vehicle != null) {
                    Double oldLatitude = vehicle.getCurrentLatitude();
                    Double oldLongitude = vehicle.getCurrentLongitude();
                    Double oldSpeed = vehicle.getSpeedKmh();

                    Vehicle updatedVehicle = vehicle.updatePosition(
                            gpsPosition.getLatitude(),
                            gpsPosition.getLongitude(),
                            gpsPosition.getSpeed(),
                            gpsPosition.getFixTime(),
                            gpsPosition.getCourse()
                    );

                    // Note: Garage detection will be done in batch after all positions are updated
                    // to avoid blocking each update with async calls
                    vehiclesToUpdate.add(updatedVehicle);

                    boolean shouldPublish = positionChangeDetector.hasSignificantChange(
                            oldLatitude, oldLongitude, oldSpeed,
                            updatedVehicle.getCurrentLatitude(),
                            updatedVehicle.getCurrentLongitude(),
                            updatedVehicle.getSpeedKmh()
                    );

                    if (shouldPublish) {
                        VehiclePositionUpdatedEvent event = new VehiclePositionUpdatedEvent(
                                updatedVehicle.getId().getValue(),
                                updatedVehicle.getDeviceId(),
                                updatedVehicle.getLicensePlate(),
                                updatedVehicle.getRouteNumber(),
                                updatedVehicle.getCurrentLatitude(),
                                updatedVehicle.getCurrentLongitude(),
                                updatedVehicle.getSpeedKmh(),
                                updatedVehicle.getIsInMotion(),
                                updatedVehicle.getLastPositionUpdate(),
                                updatedVehicle.getCourse(),
                                updatedVehicle.getCurrentDirection()
                        );
                        eventBus.publish(event);
                    }

                    statuses.add(VehicleUpdateStatus.updated(
                            updatedVehicle.getId().getValue(),
                            updatedVehicle.getDeviceId(),
                            updatedVehicle.getLicensePlate()
                    ));

                } else {
                    licensePlateExtractor.extractFromGpsData(gpsPosition)
                            .ifPresentOrElse(
                                    licensePlate -> {
                                        Vehicle newVehicle = Vehicle.create(gpsPosition.getDeviceId(), licensePlate);
                                        Vehicle positionedVehicle = newVehicle.updatePosition(
                                                gpsPosition.getLatitude(),
                                                gpsPosition.getLongitude(),
                                                gpsPosition.getSpeed(),
                                                gpsPosition.getFixTime(),
                                                gpsPosition.getCourse()
                                        );
                                        vehiclesToCreate.add(positionedVehicle);
                                        statuses.add(VehicleUpdateStatus.created(
                                                positionedVehicle.getId().getValue(),
                                                positionedVehicle.getDeviceId(),
                                                positionedVehicle.getLicensePlate()
                                        ));
                                    },
                                    () -> statuses.add(VehicleUpdateStatus.invalid(
                                            gpsPosition.getDeviceId(),
                                            "Cannot extract license plate"
                                    ))
                            );
                }
            } catch (IllegalArgumentException e) {
                log.warn("Invalid GPS data for device {}: {}", gpsPosition.getDeviceId(), e.getMessage());
                statuses.add(VehicleUpdateStatus.invalid(gpsPosition.getDeviceId(), e.getMessage()));
            }
        }

        // Update directions for vehicles with assigned routes before saving
        Mono<List<Vehicle>> vehiclesWithDirections = vehiclesToUpdate.isEmpty() ?
                Mono.just(List.of()) :
                directionDetectionService.updateVehicleDirectionsBatch(vehiclesToUpdate)
                        .doOnNext(updated -> log.debug("Direction detection completed for {} vehicles", updated.size()));

        // Detect garage entry/exit for all vehicles
        Mono<List<Vehicle>> vehiclesWithGarageDetection = vehiclesWithDirections.flatMap(vehicles -> {
            if (vehicles.isEmpty()) {
                return Mono.just(List.of());
            }

            return Flux.fromIterable(vehicles)
                    .flatMap(this::detectAndHandleGarageTransition)
                    .collectList()
                    .doOnNext(updated -> log.debug("Garage detection completed for {} vehicles", updated.size()));
        });

        return vehiclesWithGarageDetection.flatMap(updatedVehicles -> {
            Mono<Integer> updateMono = updatedVehicles.isEmpty() ?
                    Mono.just(0) : vehicleRepository.batchUpdate(updatedVehicles);

            Mono<List<Vehicle>> insertMono = vehiclesToCreate.isEmpty() ?
                    Mono.just(List.of()) : vehicleRepository.batchInsert(vehiclesToCreate).collectList();

            // Save GPS history for route detection
            Mono<Void> saveHistoryMono = Flux.fromIterable(updatedVehicles)
                    .filter(Vehicle::hasPosition)
                    .flatMap(v -> gpsHistoryService.addPoint(
                            v.getId().getValue(),
                            v.getCurrentLatitude(),
                            v.getCurrentLongitude(),
                            v.getSpeedKmh(),
                            v.getLastPositionUpdate()
                    ))
                    .then();

            // Process expired assignments reactively during GPS update
            Mono<ProcessExpiredAssignmentsUseCase.Result> expiredMono =
                    processExpiredAssignmentsUseCase.execute(Mono.empty());

            return Mono.zip(updateMono, insertMono)
                    .flatMap(tuple -> saveHistoryMono.thenReturn(tuple))
                    .flatMap(tuple -> expiredMono.thenReturn(tuple))
                    .map(tuple -> {
                        log.info("Batch operations: {} updated, {} created", tuple.getT1(), tuple.getT2().size());
                        return createResult(statuses);
                    });
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

            Vehicle updatedVehicle = vehicle.updatePosition(
                    gpsPosition.getLatitude(),
                    gpsPosition.getLongitude(),
                    gpsPosition.getSpeed(),
                    gpsPosition.getFixTime(),
                    gpsPosition.getCourse()
            );

            return vehicleRepository.save(updatedVehicle)
                    .doOnSuccess(savedVehicle -> {
                        boolean shouldPublishEvent = positionChangeDetector.hasSignificantChange(
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
                                    savedVehicle.getCourse(),
                                    savedVehicle.getCurrentDirection()
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




    private Mono<VehicleUpdateStatus> createNewVehicle(GpsPositionDTO gpsPosition) {
        return vehicleFactory.createOrFindFromGpsData(gpsPosition)
                .flatMap(vehicle -> {
                    Vehicle positionedVehicle = vehicleFactory.updateVehiclePosition(vehicle, gpsPosition);

                    return vehicleRepository.save(positionedVehicle)
                            .as(this::persistAndPublish)
                            .map(savedVehicle -> VehicleUpdateStatus.created(
                                    savedVehicle.getId().getValue(),
                                    savedVehicle.getDeviceId(),
                                    savedVehicle.getLicensePlate()
                            ))
                            .doOnSuccess(status -> log.info("Created new vehicle: {}", status));
                })
                .onErrorResume(IllegalArgumentException.class, e -> {
                    log.warn("Cannot create vehicle for device {}: {}", gpsPosition.getDeviceId(), e.getMessage());
                    return Mono.just(VehicleUpdateStatus.invalid(gpsPosition.getDeviceId(), e.getMessage()));
                });
    }


    private VehiclePositionUpdateResult createResult(List<VehicleUpdateStatus> statuses) {
        long updated = statuses.stream().mapToLong(s -> s.getType() == UpdateType.UPDATED ? 1 : 0).sum();
        long created = statuses.stream().mapToLong(s -> s.getType() == UpdateType.CREATED ? 1 : 0).sum();
        long failed = statuses.stream().mapToLong(s -> s.getType() == UpdateType.FAILED ? 1 : 0).sum();
        long invalid = statuses.stream().mapToLong(s -> s.getType() == UpdateType.INVALID ? 1 : 0).sum();
        long conflicts = statuses.stream().mapToLong(s -> s.getType() == UpdateType.CONFLICT ? 1 : 0).sum();

        return new VehiclePositionUpdateResult(
                updated, created, failed, invalid, conflicts,
                LocalDateTime.now(), statuses
        );
    }


    private Mono<Vehicle> detectAndHandleGarageTransition(Vehicle vehicle) {
        if (vehicle == null || !vehicle.hasPosition()) {
            return Mono.just(Objects.requireNonNull(vehicle));
        }

        Coordinates position = vehicle.toCoordinates();
        Double speed = vehicle.getSpeedKmh();

        boolean wasInGarage = vehicle.isCurrentlyInGarage();

        if (wasInGarage) {
            return checkGarageExit(vehicle, position, speed);
        } else {
            return checkGarageEntry(vehicle, position, speed);
        }
    }


    private Mono<Vehicle> checkGarageEntry(Vehicle vehicle, Coordinates position, Double speed) {
        return garageTransitionsUseCase.isVehicleEntryCandidate(position, speed)
                .flatMap(isCandidate -> {
                    if (!isCandidate) {
                        return Mono.just(vehicle);
                    }

                    return garageTransitionsUseCase.findGarageAtPosition(position)
                            .flatMap(garageOpt -> {
                                if (garageOpt.isPresent()) {
                                    Garage garage = garageOpt.get();
                                    log.info("Vehicle {} entering garage {} at speed {} km/h",
                                            vehicle.getLicensePlate(),
                                            garage.getName(),
                                            speed);
                                    return processGarageEntryUseCase.execute(
                                            new ProcessGarageEntryUseCase.Request(vehicle, garage.getId().getValue().toString())
                                    );
                                }
                                return Mono.just(vehicle);
                            });
                });
    }


    private Mono<Vehicle> checkGarageExit(Vehicle vehicle, Coordinates position, Double speed) {
        String lastGarageId = vehicle.getLastGarageId();
        if (lastGarageId == null) {
            return Mono.just(vehicle);
        }

        return garageTransitionsUseCase.getAllActiveGarages()
                .filter(garage -> garage.getId().getValue().toString().equals(lastGarageId))
                .next()
                .flatMap(garage -> {
                    boolean hasExited = garageTransitionsUseCase.hasVehicleExitedGarage(garage, position, speed);
                    if (hasExited) {
                        log.info("Vehicle {} exiting garage {} at speed {} km/h (distance: {} meters)",
                                vehicle.getLicensePlate(),
                                garage.getName(),
                                speed,
                                garage.calculateDistanceMeters(position));
                        return processGarageExitUseCase.execute(
                                new ProcessGarageExitUseCase.Request(vehicle)
                        );
                    }
                    return Mono.just(vehicle);
                })
                .defaultIfEmpty(vehicle);
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