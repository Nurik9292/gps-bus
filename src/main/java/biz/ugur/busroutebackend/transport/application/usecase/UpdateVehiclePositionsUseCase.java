package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionUpdateResult;
import biz.ugur.busroutebackend.transport.application.usecase.pipeline.DirectionGarageStage;
import biz.ugur.busroutebackend.transport.application.usecase.pipeline.GpsPositionResolver;
import biz.ugur.busroutebackend.transport.application.usecase.pipeline.GpsValidationStage;
import biz.ugur.busroutebackend.transport.application.usecase.pipeline.OutlierFilterStage;
import biz.ugur.busroutebackend.transport.application.usecase.pipeline.PersistAndBroadcastStage;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.service.FrozenCoordsRegistry;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.service.LicensePlateExtractor;
import biz.ugur.busroutebackend.transport.domain.service.PositionChangeDetector;
import biz.ugur.busroutebackend.transport.domain.valueobject.FailedGpsUpdate;
import biz.ugur.busroutebackend.transport.infrastructure.redis.GpsUpdateDeadLetterQueue;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import java.time.LocalDateTime;

@Service
@Slf4j
public class UpdateVehiclePositionsUseCase extends BaseUseCase<List<GpsPositionDTO>, VehiclePositionUpdateResult> {


    private final VehicleRepository vehicleRepository;
    private final PositionChangeDetector positionChangeDetector;
    private final FrozenCoordsRegistry frozenCoordsRegistry;
    private final LicensePlateExtractor licensePlateExtractor;
    private final GpsUpdateDeadLetterQueue deadLetterQueue;
    private final biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer pipelineTracer;
    private final GpsValidationStage validationStage;
    private final OutlierFilterStage outlierFilterStage;
    private final GpsPositionResolver positionResolver;
    private final DirectionGarageStage directionGarageStage;
    private final PersistAndBroadcastStage persistAndBroadcastStage;

    public UpdateVehiclePositionsUseCase(VehicleRepository vehicleRepository,
                                         PositionChangeDetector positionChangeDetector,
                                         LicensePlateExtractor licensePlateExtractor,
                                         EventBus eventBus,
                                         CorrelationContextService correlationContextService,
                                         GpsUpdateDeadLetterQueue deadLetterQueue,
                                         biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer pipelineTracer,
                                         GpsValidationStage validationStage,
                                         OutlierFilterStage outlierFilterStage,
                                         GpsPositionResolver positionResolver,
                                         DirectionGarageStage directionGarageStage,
                                         PersistAndBroadcastStage persistAndBroadcastStage,
                                         FrozenCoordsRegistry frozenCoordsRegistry) {
        super(correlationContextService, eventBus);
        this.frozenCoordsRegistry = frozenCoordsRegistry;
        this.vehicleRepository = vehicleRepository;
        this.positionChangeDetector = positionChangeDetector;
        this.licensePlateExtractor = licensePlateExtractor;
        this.deadLetterQueue = deadLetterQueue;
        this.pipelineTracer = pipelineTracer;
        this.validationStage = validationStage;
        this.outlierFilterStage = outlierFilterStage;
        this.positionResolver = positionResolver;
        this.directionGarageStage = directionGarageStage;
        this.persistAndBroadcastStage = persistAndBroadcastStage;
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
            log.debug("[GPS_PIPELINE] USE_CASE_INPUT total={} correlationId={}", gpsPositions.size(), correlationId);

            GpsValidationStage.ValidatedGpsBatch validated = validationStage.apply(gpsPositions);

            if (validated.isEmpty()) {
                return Mono.just(new VehiclePositionUpdateResult(0, 0, 0, validated.invalidCount(), 0, LocalDateTime.now(), List.of()));
            }

            List<GpsPositionDTO> idempotencyFiltered = validated.accepted();

            List<String> deviceIds = idempotencyFiltered.stream()
                    .map(GpsPositionDTO::getDeviceId)
                    .filter(deviceId -> deviceId != null && !deviceId.isBlank())
                    .distinct()
                    .toList();

            if (deviceIds.isEmpty()) {
                log.warn("[GPS_PIPELINE] No valid device IDs found after filtering {} positions", idempotencyFiltered.size());
                return Mono.just(new VehiclePositionUpdateResult(0, 0, 0, idempotencyFiltered.size(), 0, LocalDateTime.now(), List.of()));
            }

            final int validCount = idempotencyFiltered.size();
            return outlierFilterStage.apply(idempotencyFiltered)
                    .flatMap(filteredPositions -> {
                        int outlierCount = validCount - filteredPositions.size();
                        log.debug("[GPS_PIPELINE] OUTLIER_FILTER passed={} rejected={}",
                                filteredPositions.size(), outlierCount);

                        if (filteredPositions.isEmpty()) {
                            log.warn("[GPS_PIPELINE] All {} positions were filtered as outliers", validCount);
                            return Mono.just(new VehiclePositionUpdateResult(0, 0, 0, validCount, 0, LocalDateTime.now(), List.of()));
                        }

                        List<String> filteredDeviceIds = filteredPositions.stream()
                                .map(GpsPositionDTO::getDeviceId)
                                .filter(deviceId -> deviceId != null && !deviceId.isBlank())
                                .distinct()
                                .toList();

                        return vehicleRepository.findByDeviceIds(filteredDeviceIds)
                                .flatMap(existingVehiclesMap -> {
                                    for (Vehicle v : existingVehiclesMap.values()) {
                                        if (v == null) continue;
                                        pipelineTracer.rememberRoute(
                                                v.getDeviceId(),
                                                v.getId() != null ? v.getId().getValue() : null,
                                                v.getRouteNumber());
                                        pipelineTracer.traceDbReadVehicle(
                                                v.getId() != null ? v.getId().getValue() : null,
                                                v.getDeviceId(),
                                                v.getLicensePlate(),
                                                v.getRouteNumber(),
                                                v.getCurrentDirection(),
                                                v.getCurrentLatitude(),
                                                v.getCurrentLongitude(),
                                                v.getLastPositionUpdate(),
                                                v.getVersion());
                                    }
                                    return processBatch(filteredPositions, existingVehiclesMap);
                                });
                    })
                    .doOnSuccess(result -> log.debug("[GPS_PIPELINE] GPS batch update completed: {}", result.updatedCount()))
                    .onErrorResume(error -> {
                        log.error("[GPS_PIPELINE] GPS batch update failed, sending {} positions to DLQ", idempotencyFiltered.size(), error);
                        return Flux.fromIterable(idempotencyFiltered)
                                .flatMap(pos -> sendToDeadLetterQueue(pos, error))
                                .then(Mono.just(new VehiclePositionUpdateResult(
                                        0, 0, idempotencyFiltered.size(), 0, 0,
                                        LocalDateTime.now(), List.of()
                                )));
                    });
        });
    }

    private Mono<VehiclePositionUpdateResult> processBatch(List<GpsPositionDTO> validPositions, Map<String, Vehicle> existingVehicles) {
        for (GpsPositionDTO position : validPositions) {
            Vehicle v = existingVehicles.get(position.getDeviceId());
            String plate = v != null ? v.getLicensePlate() : null;
            pipelineTracer.traceGpsApiRecv(
                    position.getDeviceId(), plate,
                    position.getGpsProvider() != null ? position.getGpsProvider().name() : "UNKNOWN",
                    position.getLatitude(), position.getLongitude(),
                    String.valueOf(position.getFixTime()),
                    position.getSpeed());
        }

        Map<String, GpsPositionDTO> latestPositionsByDevice =
                positionResolver.resolveLatestByDevice(validPositions, existingVehicles);

        List<Vehicle> vehiclesToUpdate = new ArrayList<>();
        List<Vehicle> vehiclesForDetection = new ArrayList<>();
        List<Vehicle> vehiclesToCreate = new ArrayList<>();
        List<VehicleUpdateStatus> statuses = new ArrayList<>();
        Map<String, Double> estimatedBearings = new HashMap<>();
        Map<String, double[]> oldCoordsByVehicleId = new HashMap<>();
        Map<String, Boolean> hasSignificantChangeById = new HashMap<>();
        Map<String, Boolean> frozenCoordsWithMotionById = new HashMap<>();
        Set<String> frozenCoordsDeviceIds = new HashSet<>();
        Map<String, Boolean> bufferedByDeviceId = new HashMap<>();
        for (GpsPositionDTO pos : latestPositionsByDevice.values()) {
            if (pos.getDeviceId() != null) {
                bufferedByDeviceId.put(pos.getDeviceId(), pos.isLikelyBuffered());
            }
        }

        for (GpsPositionDTO gpsPosition : latestPositionsByDevice.values()) {
            try {
                Vehicle vehicle = existingVehicles.get(gpsPosition.getDeviceId());

                if (vehicle != null) {
                    Double oldLatitude = vehicle.getCurrentLatitude();
                    Double oldLongitude = vehicle.getCurrentLongitude();
                    Double oldSpeed = vehicle.getSpeedKmh();

                    if (oldLatitude != null && oldLongitude != null) {
                        oldCoordsByVehicleId.put(vehicle.getId().getValue(),
                                new double[]{oldLatitude, oldLongitude});
                    }

                    Vehicle updatedVehicle = vehicle.updatePosition(
                            gpsPosition.getLatitude(),
                            gpsPosition.getLongitude(),
                            gpsPosition.getSpeed(),
                            gpsPosition.getFixTime(),
                            gpsPosition.getCourse()
                    );

                    boolean hasSignificantChange = positionChangeDetector.hasSignificantChange(
                            oldLatitude, oldLongitude, oldSpeed,
                            updatedVehicle.getCurrentLatitude(),
                            updatedVehicle.getCurrentLongitude(),
                            updatedVehicle.getSpeedKmh()
                    );

                    boolean coordsMovedSignificantly = oldLatitude != null && oldLongitude != null
                            && positionChangeDetector.hasSignificantPositionChange(
                                    oldLatitude, oldLongitude,
                                    updatedVehicle.getCurrentLatitude(),
                                    updatedVehicle.getCurrentLongitude());

                    boolean frozenCoordsWithMotion = hasSignificantChange
                            && Boolean.TRUE.equals(updatedVehicle.getIsInMotion())
                            && oldLatitude != null && oldLongitude != null
                            && !coordsMovedSignificantly;

                    if (frozenCoordsWithMotion) {
                        if (gpsPosition.getDeviceId() != null) {
                            var episode = frozenCoordsRegistry.recordFrozenEvent(
                                    gpsPosition.getDeviceId(),
                                    updatedVehicle.getLicensePlate(),
                                    updatedVehicle.getRouteNumber(),
                                    updatedVehicle.getSpeedKmh());
                            if (episode.warnAllowed()) {
                                log.warn("[GPS_ANOMALY|SOURCE:SERVER] FROZEN_COORDS_WITH_MOTION: " +
                                                "device={}, plate={}, speed={}km/h, coords=({},{}), "
                                                + "frozenSince={}, detections={} — suppressing WS publish",
                                        gpsPosition.getDeviceId(),
                                        updatedVehicle.getLicensePlate(),
                                        String.format("%.1f", updatedVehicle.getSpeedKmh()),
                                        String.format("%.6f", updatedVehicle.getCurrentLatitude()),
                                        String.format("%.6f", updatedVehicle.getCurrentLongitude()),
                                        episode.firstFrozenAt(), episode.detectionCount());
                            }
                            frozenCoordsDeviceIds.add(gpsPosition.getDeviceId());
                        }
                    } else if (coordsMovedSignificantly && gpsPosition.getDeviceId() != null) {
                        frozenCoordsRegistry.recordCoordinatesMoved(gpsPosition.getDeviceId());
                    }

                    pipelineTracer.traceDbSave(
                            gpsPosition.getDeviceId(),
                            updatedVehicle.getLicensePlate(),
                            oldLatitude, oldLongitude,
                            updatedVehicle.getCurrentLatitude(),
                            updatedVehicle.getCurrentLongitude(),
                            hasSignificantChange,
                            hasSignificantChange ? "significant-change" : "no-change-skip");

                    vehiclesToUpdate.add(updatedVehicle);

                    if (hasSignificantChange) {
                        boolean hasCourse = updatedVehicle.getCourse() != null
                                && updatedVehicle.getCourse() > 0;
                        if (!hasCourse && oldLatitude != null && oldLongitude != null) {
                            double bearing = computeBearing(oldLatitude, oldLongitude,
                                    updatedVehicle.getCurrentLatitude(),
                                    updatedVehicle.getCurrentLongitude());
                            vehiclesForDetection.add(
                                    updatedVehicle.toBuilder().course(bearing).build());
                            estimatedBearings.put(updatedVehicle.getId().getValue(), bearing);
                            log.debug("[GPS_PIPELINE] DIR_BEARING vehicle={} plate={} bearing={} (pos-delta)",
                                    updatedVehicle.getId().getValue(),
                                    updatedVehicle.getLicensePlate(),
                                    String.format("%.1f°", bearing));
                        } else {
                            vehiclesForDetection.add(updatedVehicle);
                        }
                    } else {
                        vehiclesForDetection.add(updatedVehicle);
                    }

                    String vehicleId = updatedVehicle.getId().getValue();

                    hasSignificantChangeById.put(vehicleId, hasSignificantChange);
                    frozenCoordsWithMotionById.put(vehicleId, frozenCoordsWithMotion);

                    statuses.add(VehicleUpdateStatus.updated(
                            updatedVehicle.getId().getValue(),
                            updatedVehicle.getDeviceId(),
                            updatedVehicle.getLicensePlate()
                    ));

                } else {
                    licensePlateExtractor.extractFromGpsData(gpsPosition)
                            .ifPresentOrElse(
                                    licensePlate -> {
                                        Vehicle newVehicle = Vehicle.create(gpsPosition.getDeviceId(), licensePlate,
                                                gpsPosition.getGpsProvider());
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
                log.warn("[GPS_PIPELINE] Invalid GPS data for device {}: {}", gpsPosition.getDeviceId(), e.getMessage());
                statuses.add(VehicleUpdateStatus.invalid(gpsPosition.getDeviceId(), e.getMessage()));
            }
        }

        Mono<List<Vehicle>> vehiclesWithGarageDetection =
                directionGarageStage.apply(vehiclesToUpdate, vehiclesForDetection);

        return vehiclesWithGarageDetection.flatMap(updatedVehicles -> {
            PersistAndBroadcastStage.Context ctx = new PersistAndBroadcastStage.Context(
                    updatedVehicles,
                    vehiclesToCreate,
                    estimatedBearings,
                    oldCoordsByVehicleId,
                    hasSignificantChangeById,
                    frozenCoordsWithMotionById,
                    frozenCoordsDeviceIds,
                    bufferedByDeviceId,
                    latestPositionsByDevice
            );
            return persistAndBroadcastStage.apply(ctx)
                    .thenReturn(createResult(statuses));
        });
    }


    private static double computeBearing(double lat1, double lon1, double lat2, double lon2) {
        double lat1r = Math.toRadians(lat1);
        double lat2r = Math.toRadians(lat2);
        double dLon  = Math.toRadians(lon2 - lon1);
        double x = Math.sin(dLon) * Math.cos(lat2r);
        double y = Math.cos(lat1r) * Math.sin(lat2r)
                 - Math.sin(lat1r) * Math.cos(lat2r) * Math.cos(dLon);
        return (Math.toDegrees(Math.atan2(x, y)) + 360) % 360;
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