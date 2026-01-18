package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.geospatial.application.usecase.DetectGarageTransitionsUseCase;
import biz.ugur.busroutebackend.geospatial.application.usecase.ProcessGarageEntryUseCase;
import biz.ugur.busroutebackend.geospatial.application.usecase.ProcessGarageExitUseCase;
import biz.ugur.busroutebackend.geospatial.domain.model.Garage;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.shared.domain.event.DomainEventPublisher;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionUpdateResult;
import biz.ugur.busroutebackend.transport.application.factory.VehicleFactory;
import biz.ugur.busroutebackend.transport.domain.event.VehiclePositionUpdatedEvent;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.service.GpsOutlierDetector;
import biz.ugur.busroutebackend.transport.domain.service.LicensePlateExtractor;
import biz.ugur.busroutebackend.transport.domain.service.PositionChangeDetector;
import biz.ugur.busroutebackend.transport.domain.service.VehicleDirectionDetectionService;
import biz.ugur.busroutebackend.transport.domain.service.VehicleValidationService;
import biz.ugur.busroutebackend.transport.domain.valueobject.FailedGpsUpdate;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsValidationResult;
import biz.ugur.busroutebackend.transport.domain.valueobject.OutlierDetectionResult;
import biz.ugur.busroutebackend.transport.infrastructure.config.GpsOutlierDetectionProperties;
import biz.ugur.busroutebackend.transport.infrastructure.metrics.GpsOutlierMetricsRecorder;
import biz.ugur.busroutebackend.transport.infrastructure.metrics.GpsValidationMetricsRecorder;
import biz.ugur.busroutebackend.transport.infrastructure.redis.GpsPoint;
import biz.ugur.busroutebackend.transport.infrastructure.redis.GpsUpdateDeadLetterQueue;
import biz.ugur.busroutebackend.transport.infrastructure.redis.VehicleGpsHistoryService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import java.time.Instant;
import java.time.LocalDateTime;

@Service
@Slf4j
public class UpdateVehiclePositionsUseCase extends BaseUseCase<List<GpsPositionDTO>, VehiclePositionUpdateResult> {

    private static final long FORCE_PUBLISH_INTERVAL_SECONDS = 30;

    private final ConcurrentHashMap<String, Instant> lastPublishedTime = new ConcurrentHashMap<>();

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
    private final DomainEventPublisher domainEventPublisher;
    private final GpsValidationMetricsRecorder validationMetricsRecorder;
    private final GpsUpdateDeadLetterQueue deadLetterQueue;
    private final GpsOutlierDetector outlierDetector;
    private final GpsOutlierMetricsRecorder outlierMetricsRecorder;
    private final GpsOutlierDetectionProperties outlierDetectionProperties;

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
                                         EventBus eventBus,
                                         DomainEventPublisher domainEventPublisher,
                                         CorrelationContextService correlationContextService,
                                         GpsValidationMetricsRecorder validationMetricsRecorder,
                                         GpsUpdateDeadLetterQueue deadLetterQueue,
                                         GpsOutlierDetector outlierDetector,
                                         GpsOutlierMetricsRecorder outlierMetricsRecorder,
                                         GpsOutlierDetectionProperties outlierDetectionProperties) {
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
        this.domainEventPublisher = domainEventPublisher;
        this.validationMetricsRecorder = validationMetricsRecorder;
        this.deadLetterQueue = deadLetterQueue;
        this.outlierDetector = outlierDetector;
        this.outlierMetricsRecorder = outlierMetricsRecorder;
        this.outlierDetectionProperties = outlierDetectionProperties;
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
            log.debug("Processing {} GPS positions (batch mode) - CorrelationId: {}", gpsPositions.size(), correlationId);

            List<GpsPositionDTO> validPositions = new ArrayList<>();
            Map<GpsValidationResult, Long> validationCounts = new EnumMap<>(GpsValidationResult.class);
            for (GpsValidationResult r : GpsValidationResult.values()) {
                validationCounts.put(r, 0L);
            }

            for (GpsPositionDTO position : gpsPositions) {
                GpsValidationResult result = validationService.validateGpsPosition(position);
                validationCounts.merge(result, 1L, Long::sum);
                if (result.isValid()) {
                    validPositions.add(position);
                }
            }

            validationMetricsRecorder.recordBatch(validationCounts);

            long invalidCount = gpsPositions.size() - validPositions.size();
            if (invalidCount > 0) {
                log.debug("Validation: total={}, valid={}, invalid={}",
                        gpsPositions.size(), validPositions.size(), invalidCount);
            }

            if (validPositions.isEmpty()) {
                log.warn("All positions failed validation: {}", validationCounts);
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

            final int validCount = validPositions.size();
            return filterOutliers(validPositions)
                    .flatMap(filteredPositions -> {
                        int outlierCount = validCount - filteredPositions.size();
                        if (outlierCount > 0) {
                            log.debug("Outlier filter: {} of {} positions filtered",
                                    outlierCount, validCount);
                        }

                        if (filteredPositions.isEmpty()) {
                            log.warn("All {} positions were filtered as outliers", validCount);
                            return Mono.just(new VehiclePositionUpdateResult(0, 0, 0, validCount, 0, LocalDateTime.now(), List.of()));
                        }

                        List<String> filteredDeviceIds = filteredPositions.stream()
                                .map(GpsPositionDTO::getDeviceId)
                                .filter(deviceId -> deviceId != null && !deviceId.isBlank())
                                .distinct()
                                .toList();

                        return vehicleRepository.findByDeviceIds(filteredDeviceIds)
                                .flatMap(existingVehiclesMap -> processBatch(filteredPositions, existingVehiclesMap));
                    })
                    .doOnSuccess(result -> log.debug("GPS batch update completed: {}", result.updatedCount()))
                    .onErrorResume(error -> {
                        log.error("GPS batch update failed, sending {} positions to DLQ", validPositions.size(), error);
                        return Flux.fromIterable(validPositions)
                                .flatMap(pos -> sendToDeadLetterQueue(pos, error))
                                .then(Mono.just(new VehiclePositionUpdateResult(
                                        0, 0, validPositions.size(), 0, 0,
                                        LocalDateTime.now(), List.of()
                                )));
                    });
        });
    }

    private static final int OUTLIER_DETECTION_CONCURRENCY = 16;
    private static final int GARAGE_DETECTION_CONCURRENCY = 8;

    private Mono<List<GpsPositionDTO>> filterOutliers(List<GpsPositionDTO> positions) {
        if (!outlierDetectionProperties.isEnabled()) {
            return Mono.just(positions);
        }

        int historyLimit = outlierDetectionProperties.getHistoryPointsToCheck();
        boolean rejectOutliers = outlierDetectionProperties.isRejectOutliers();

        return Flux.fromIterable(positions)
                .flatMap(position -> detectOutlierForPosition(position, historyLimit)
                        .map(result -> new PositionWithOutlierResult(position, result)),
                        OUTLIER_DETECTION_CONCURRENCY)
                .collectList()
                .map(results -> {
                    List<GpsPositionDTO> nonOutliers = new ArrayList<>();
                    List<OutlierDetectionResult> allResults = new ArrayList<>();

                    for (PositionWithOutlierResult result : results) {
                        allResults.add(result.detectionResult());

                        if (!result.detectionResult().isOutlier() || !rejectOutliers) {
                            nonOutliers.add(result.position());
                        }
                    }

                    outlierMetricsRecorder.recordBatch(allResults);

                    int outliersDetected = (int) allResults.stream().filter(OutlierDetectionResult::isOutlier).count();
                    if (outliersDetected > 0) {
                        log.info("Outlier detection: {} outliers detected out of {} positions, {} rejected",
                                outliersDetected, positions.size(), rejectOutliers ? outliersDetected : 0);
                    }

                    return nonOutliers;
                });
    }

    private Mono<OutlierDetectionResult> detectOutlierForPosition(GpsPositionDTO position, int historyLimit) {
        String deviceId = position.getDeviceId();

        return gpsHistoryService.getHistoryList(deviceId, historyLimit)
                .map(history -> outlierDetector.detectWithHistory(
                        deviceId,
                        position.getLatitude(),
                        position.getLongitude(),
                        position.getFixTime(),
                        history
                ))
                .onErrorResume(error -> {
                    log.warn("Failed to check outlier for device {}: {}", deviceId, error.getMessage());
                    return Mono.just(OutlierDetectionResult.noHistory(deviceId,
                            outlierDetectionProperties.getMaxImpliedSpeedKmh()));
                });
    }

    private record PositionWithOutlierResult(GpsPositionDTO position, OutlierDetectionResult detectionResult) {}

    private Mono<VehiclePositionUpdateResult> processBatch(List<GpsPositionDTO> validPositions, Map<String, Vehicle> existingVehicles) {
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

                    vehiclesToUpdate.add(updatedVehicle);

                    boolean hasSignificantChange = positionChangeDetector.hasSignificantChange(
                            oldLatitude, oldLongitude, oldSpeed,
                            updatedVehicle.getCurrentLatitude(),
                            updatedVehicle.getCurrentLongitude(),
                            updatedVehicle.getSpeedKmh()
                    );

                    String vehicleId = updatedVehicle.getId().getValue();
                    boolean shouldForcePublish = shouldForcePublishForVehicle(vehicleId);
                    boolean shouldPublish = hasSignificantChange || shouldForcePublish;

                    if (shouldPublish) {
                        lastPublishedTime.put(vehicleId, Instant.now());

                        VehiclePositionUpdatedEvent event = new VehiclePositionUpdatedEvent(
                                vehicleId,
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
                        domainEventPublisher.publish(event);
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

        Mono<List<Vehicle>> vehiclesWithDirections = vehiclesToUpdate.isEmpty() ?
                Mono.just(List.of()) :
                directionDetectionService.updateVehicleDirectionsBatch(vehiclesToUpdate)
                        .doOnNext(updated -> log.debug("Direction detection completed for {} vehicles", updated.size()));

        Mono<List<Vehicle>> vehiclesWithGarageDetection = vehiclesWithDirections.flatMap(vehicles -> {
            if (vehicles.isEmpty()) {
                return Mono.just(List.of());
            }

            return Flux.fromIterable(vehicles)
                    .flatMap(this::detectAndHandleGarageTransition, GARAGE_DETECTION_CONCURRENCY)
                    .collectList()
                    .doOnNext(updated -> log.debug("Garage detection completed for {} vehicles", updated.size()));
        });

        return vehiclesWithGarageDetection.flatMap(updatedVehicles -> {
            Mono<Integer> updateMono = updatedVehicles.isEmpty() ?
                    Mono.just(0) : vehicleRepository.batchUpdate(updatedVehicles);

            Mono<List<Vehicle>> insertMono = vehiclesToCreate.isEmpty() ?
                    Mono.just(List.of()) : vehicleRepository.batchInsert(vehiclesToCreate).collectList();

            return Mono.zip(updateMono, insertMono)
                    .flatMap(tuple -> {
                        long vehiclesWithPosition = updatedVehicles.stream()
                                .filter(Vehicle::hasPosition)
                                .count();

                        if (vehiclesWithPosition == 0) {
                            return Mono.just(tuple);
                        }

                        return Flux.fromIterable(updatedVehicles)
                                .filter(Vehicle::hasPosition)
                                .flatMap(v -> gpsHistoryService.addPoint(
                                        v.getId().getValue(),
                                        v.getCurrentLatitude(),
                                        v.getCurrentLongitude(),
                                        v.getSpeedKmh(),
                                        v.getLastPositionUpdate()
                                ), 10)
                                .then(Mono.defer(() -> {
                                    log.debug("Saved GPS history for {} vehicles", vehiclesWithPosition);
                                    return Mono.just(tuple);
                                }));
                    })
                    .map(tuple -> {
                        log.debug("Batch operations: {} updated, {} created", tuple.getT1(), tuple.getT2().size());
                        return createResult(statuses);
                    });
        });
    }


    private boolean shouldForcePublishForVehicle(String vehicleId) {
        Instant lastPublished = lastPublishedTime.get(vehicleId);
        if (lastPublished == null) {
            return true;
        }
        long secondsSinceLastPublish = Instant.now().getEpochSecond() - lastPublished.getEpochSecond();
        return secondsSinceLastPublish >= FORCE_PUBLISH_INTERVAL_SECONDS;
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
                .flatMap(garage -> garageTransitionsUseCase.hasVehicleExitedGarage(garage, position, speed)
                        .flatMap(hasExited -> {
                            if (hasExited) {
                                log.info("Vehicle {} exiting garage {} at speed {} km/h",
                                        vehicle.getLicensePlate(),
                                        garage.getName(),
                                        speed);
                                return processGarageExitUseCase.execute(
                                        new ProcessGarageExitUseCase.Request(vehicle)
                                );
                            }
                            return Mono.just(vehicle);
                        }))
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

    private Mono<Void> sendToDeadLetterQueue(GpsPositionDTO gpsPosition, Throwable error) {
        FailedGpsUpdate.FailureType failureType = classifyFailure(error);
        String failureReason = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();

        FailedGpsUpdate failedUpdate = FailedGpsUpdate.create(
                gpsPosition.getDeviceId(),
                gpsPosition.getVehicleName(),
                gpsPosition.getLatitude(),
                gpsPosition.getLongitude(),
                gpsPosition.getSpeed(),
                gpsPosition.getCourse(),
                gpsPosition.getFixTime(),
                failureReason,
                failureType
        );

        return deadLetterQueue.enqueue(failedUpdate);
    }

    private FailedGpsUpdate.FailureType classifyFailure(Throwable error) {
        String errorClass = error.getClass().getName();
        String message = error.getMessage() != null ? error.getMessage().toLowerCase() : "";

        if (errorClass.contains("OptimisticLocking") || message.contains("optimistic")) {
            return FailedGpsUpdate.FailureType.OPTIMISTIC_LOCK_EXHAUSTED;
        }
        if (errorClass.contains("Constraint") || message.contains("constraint") ||
                message.contains("duplicate") || message.contains("foreign key")) {
            return FailedGpsUpdate.FailureType.CONSTRAINT_VIOLATION;
        }
        if (errorClass.contains("Timeout") || message.contains("timeout") || message.contains("timed out")) {
            return FailedGpsUpdate.FailureType.TIMEOUT;
        }
        if (errorClass.contains("Connection") || message.contains("connection") ||
                message.contains("network") || message.contains("refused")) {
            return FailedGpsUpdate.FailureType.CONNECTION_ERROR;
        }
        if (errorClass.contains("Validation") || message.contains("validation") ||
                message.contains("invalid")) {
            return FailedGpsUpdate.FailureType.VALIDATION_ERROR;
        }

        return FailedGpsUpdate.FailureType.UNKNOWN;
    }
}