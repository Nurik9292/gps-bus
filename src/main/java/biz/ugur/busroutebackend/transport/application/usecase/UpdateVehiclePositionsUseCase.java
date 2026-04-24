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
import biz.ugur.busroutebackend.transport.infrastructure.prediction.GatekeeperDecision;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePositionPredictionService;
import biz.ugur.busroutebackend.transport.infrastructure.redis.GpsPoint;
import biz.ugur.busroutebackend.transport.infrastructure.redis.GpsUpdateDeadLetterQueue;
import biz.ugur.busroutebackend.transport.infrastructure.redis.VehicleGpsHistoryService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@Slf4j
public class UpdateVehiclePositionsUseCase extends BaseUseCase<List<GpsPositionDTO>, VehiclePositionUpdateResult> {

    private static final long FORCE_PUBLISH_INTERVAL_SECONDS = 20;
    private static final long MAX_GPS_AGE_SECONDS = 120;
    private static final long IDEMPOTENCY_TTL_SECONDS = 60;

    private final ConcurrentHashMap<String, Instant> lastPublishedTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> lastProcessedFixTimeByDevice = new ConcurrentHashMap<>();

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
    private final VehiclePositionPredictionService predictionService;
    private final biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer pipelineTracer;

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
                                         GpsOutlierDetectionProperties outlierDetectionProperties,
                                         VehiclePositionPredictionService predictionService,
                                         biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer pipelineTracer) {
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
        this.predictionService = predictionService;
        this.pipelineTracer = pipelineTracer;
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
            log.debug("[GPS_PIPELINE] VALIDATION total={} valid={} invalid={}",
                    gpsPositions.size(), validPositions.size(), invalidCount);

            if (validPositions.isEmpty()) {
                log.warn("[GPS_PIPELINE] All positions failed validation: {}", validationCounts);
                return Mono.just(new VehiclePositionUpdateResult(0, 0, 0, gpsPositions.size() - validPositions.size(), 0, LocalDateTime.now(), List.of()));
            }

            final LocalDateTime staleCutoff = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(MAX_GPS_AGE_SECONDS);
            final List<GpsPositionDTO> freshPositions = validPositions.stream()
                    .filter(pos -> pos.getFixTime() != null && !pos.getFixTime().isBefore(staleCutoff))
                    .toList();
            if (freshPositions.size() < validPositions.size()) {
                log.debug("[GPS_PIPELINE] STALE_FILTER dropped={}/{} (age > {}s)",
                        validPositions.size() - freshPositions.size(), validPositions.size(), MAX_GPS_AGE_SECONDS);
            }
            log.debug("[GPS_PIPELINE] STALE_FILTER passed={} dropped={}",
                    freshPositions.size(), validPositions.size() - freshPositions.size());
            if (freshPositions.isEmpty()) {
                return Mono.just(new VehiclePositionUpdateResult(0, 0, 0, validPositions.size(), 0, LocalDateTime.now(), List.of()));
            }

            final List<GpsPositionDTO> highQualityPositions = freshPositions.stream()
                    .filter(pos -> {
                        if (pos.isHighQualityFix()) {
                            return true;
                        }
                        log.debug("[GPS_PIPELINE] QUALITY_REJECTED device={} hdop={} sat={}",
                                pos.getDeviceId(), pos.getHdop(), pos.getSatellites());
                        return false;
                    })
                    .toList();
            int qualityDropped = freshPositions.size() - highQualityPositions.size();
            if (qualityDropped > 0) {
                log.debug("[GPS_PIPELINE] QUALITY_FILTER passed={} rejected={}",
                        highQualityPositions.size(), qualityDropped);
            }
            if (highQualityPositions.isEmpty()) {
                return Mono.just(new VehiclePositionUpdateResult(0, 0, 0, freshPositions.size(), 0, LocalDateTime.now(), List.of()));
            }

            final List<GpsPositionDTO> idempotencyFiltered = highQualityPositions.stream()
                    .filter(pos -> {
                        String deviceId = pos.getDeviceId();
                        LocalDateTime fixTime = pos.getFixTime();
                        if (deviceId == null || fixTime == null) {
                            return true;
                        }
                        LocalDateTime lastSeen = lastProcessedFixTimeByDevice.get(deviceId);
                        return lastSeen == null || fixTime.isAfter(lastSeen);
                    })
                    .toList();
            int duplicateDropped = highQualityPositions.size() - idempotencyFiltered.size();
            if (duplicateDropped > 0) {
                log.debug("[GPS_PIPELINE] IDEMPOTENCY_FILTER passed={} duplicates_skipped={}",
                        idempotencyFiltered.size(), duplicateDropped);
            }
            if (idempotencyFiltered.isEmpty()) {
                log.debug("[GPS_PIPELINE] CYCLE_SKIPPED_NO_FRESH_DATA all {} positions already processed",
                        highQualityPositions.size());
                return Mono.just(new VehiclePositionUpdateResult(0, 0, 0, 0, 0, LocalDateTime.now(), List.of()));
            }
            for (GpsPositionDTO pos : idempotencyFiltered) {
                if (pos.getDeviceId() != null && pos.getFixTime() != null) {
                    lastProcessedFixTimeByDevice.merge(pos.getDeviceId(), pos.getFixTime(),
                            (existing, incoming) -> incoming.isAfter(existing) ? incoming : existing);
                }
            }

            List<String> deviceIds = idempotencyFiltered.stream()
                    .map(GpsPositionDTO::getDeviceId)
                    .filter(deviceId -> deviceId != null && !deviceId.isBlank())
                    .distinct()
                    .toList();

            if (deviceIds.isEmpty()) {
                log.warn("[GPS_PIPELINE] No valid device IDs found after filtering {} positions", freshPositions.size());
                return Mono.just(new VehiclePositionUpdateResult(0, 0, 0, freshPositions.size(), 0, LocalDateTime.now(), List.of()));
            }

            final int validCount = idempotencyFiltered.size();
            return filterOutliers(idempotencyFiltered)
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
                                .flatMap(existingVehiclesMap -> processBatch(filteredPositions, existingVehiclesMap));
                    })
                    .doOnSuccess(result -> log.debug("[GPS_PIPELINE] GPS batch update completed: {}", result.updatedCount()))
                    .onErrorResume(error -> {
                        log.error("[GPS_PIPELINE] GPS batch update failed, sending {} positions to DLQ", validPositions.size(), error);
                        return Flux.fromIterable(validPositions)
                                .flatMap(pos -> sendToDeadLetterQueue(pos, error))
                                .then(Mono.just(new VehiclePositionUpdateResult(
                                        0, 0, validPositions.size(), 0, 0,
                                        LocalDateTime.now(), List.of()
                                )));
                    });
        });
    }

    private static final int OUTLIER_DETECTION_CONCURRENCY = 32;
    private static final int GARAGE_DETECTION_CONCURRENCY = 2;

    private Mono<List<GpsPositionDTO>> filterOutliers(List<GpsPositionDTO> positions) {
        if (!outlierDetectionProperties.isEnabled()) {
            return Mono.just(positions);
        }

        int historyLimit = outlierDetectionProperties.getHistoryPointsToCheck();
        boolean rejectOutliers = outlierDetectionProperties.isRejectOutliers();
        boolean rejectFrozenMotion = outlierDetectionProperties.isRejectFrozenMotion();

        List<String> deviceIds = positions.stream()
                .map(GpsPositionDTO::getDeviceId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        return gpsHistoryService.getHistoryBatch(deviceIds, historyLimit)
                .flatMap(historyByDevice -> Flux.fromIterable(positions)
                        .map(position -> {
                            var history = historyByDevice.getOrDefault(position.getDeviceId(), List.of());
                            OutlierDetectionResult detection = outlierDetector.detectWithHistory(
                                    position.getDeviceId(),
                                    position.getLatitude(),
                                    position.getLongitude(),
                                    position.getFixTime(),
                                    history,
                                    position.getSpeed()
                            );
                            return new PositionWithOutlierResult(position, detection);
                        })
                        .collectList())
                .map(results -> {
                    List<GpsPositionDTO> nonOutliers = new ArrayList<>();
                    List<OutlierDetectionResult> allResults = new ArrayList<>();
                    int teleportRejected = 0;
                    int frozenRejected = 0;

                    for (PositionWithOutlierResult r : results) {
                        allResults.add(r.detectionResult());
                        OutlierDetectionResult.OutlierType type = r.detectionResult().type();

                        boolean isTeleportation = type == OutlierDetectionResult.OutlierType.SPEED_EXCEEDED;
                        boolean isFrozen = type == OutlierDetectionResult.OutlierType.FROZEN_COORDINATES_WITH_MOTION;

                        if (isTeleportation && rejectOutliers) {
                            teleportRejected++;
                            log.warn("[GPS_ANOMALY|SOURCE:SERVER] TELEPORTATION_REJECTED: " +
                                            "device={}, impliedSpeed={}km/h — {}",
                                    r.detectionResult().deviceId(),
                                    String.format("%.1f", r.detectionResult().impliedSpeedKmh()),
                                    r.detectionResult().getDescription());
                            continue;
                        }

                        if (isFrozen && rejectFrozenMotion) {
                            frozenRejected++;
                            continue;
                        }

                        nonOutliers.add(r.position());
                    }

                    outlierMetricsRecorder.recordBatch(allResults);

                    int totalRejected = teleportRejected + frozenRejected;
                    if (totalRejected > 0) {
                        log.info("[GPS_PIPELINE] Outlier filter: {}/{} positions rejected (teleport={}, frozen={})",
                                totalRejected, positions.size(), teleportRejected, frozenRejected);
                    }

                    return nonOutliers;
                });
    }

    private record PositionWithOutlierResult(GpsPositionDTO position, OutlierDetectionResult detectionResult) {}

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

        Map<String, GpsPositionDTO> latestPositionsByDevice = new java.util.LinkedHashMap<>();
        java.util.Map<String, java.util.List<GpsPositionDTO>> duplicatesByDevice = new java.util.HashMap<>();
        for (GpsPositionDTO position : validPositions) {
            String deviceId = position.getDeviceId();
            GpsPositionDTO existing = latestPositionsByDevice.get(deviceId);

            if (existing != null) {
                duplicatesByDevice.computeIfAbsent(deviceId, k -> new java.util.ArrayList<>()).add(existing);
            }

            if (existing == null
                    || position.getFixTime() != null
                    && (existing.getFixTime() == null
                        || position.getFixTime().isAfter(existing.getFixTime()))) {
                latestPositionsByDevice.put(deviceId, position);
            }
        }

        for (Map.Entry<String, GpsPositionDTO> e : latestPositionsByDevice.entrySet()) {
            GpsPositionDTO winner = e.getValue();
            Vehicle v = existingVehicles.get(e.getKey());
            String plate = v != null ? v.getLicensePlate() : null;
            int candidatesCount = 1 + (duplicatesByDevice.containsKey(e.getKey())
                    ? duplicatesByDevice.get(e.getKey()).size() : 0);
            pipelineTracer.traceBatchWinner(e.getKey(), plate,
                    winner.getGpsProvider() != null ? winner.getGpsProvider().name() : "UNKNOWN",
                    candidatesCount,
                    String.valueOf(winner.getFixTime()));
        }

        for (var entry : duplicatesByDevice.entrySet()) {
            String deviceId = entry.getKey();
            GpsPositionDTO winner = latestPositionsByDevice.get(deviceId);
            java.util.List<GpsPositionDTO> losers = entry.getValue();
            losers.add(winner);
            Vehicle v = existingVehicles.get(deviceId);
            String plate = v != null ? v.getLicensePlate() : "?";
            StringBuilder sb = new StringBuilder();
            for (GpsPositionDTO p : losers) {
                double distFromWinner = (p == winner || p.getLatitude() == null || winner.getLatitude() == null)
                        ? 0.0
                        : biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService
                                .haversineDistanceMeters(p.getLatitude(), p.getLongitude(),
                                        winner.getLatitude(), winner.getLongitude());
                sb.append(String.format(" [%s %s lat=%.5f lon=%.5f fixTime=%s dist=%.0fm]",
                        p.getGpsProvider(), p == winner ? "WINNER" : "loser",
                        p.getLatitude() != null ? p.getLatitude() : 0.0,
                        p.getLongitude() != null ? p.getLongitude() : 0.0,
                        p.getFixTime(),
                        distFromWinner));
            }
            log.info("[GPS_PIPELINE] GPS_DUPLICATE_SOURCES device={} plate={} winner={} candidates={}{}",
                    deviceId, plate, winner.getGpsProvider(), losers.size(), sb);
        }

        for (GpsPositionDTO winner : latestPositionsByDevice.values()) {
            String deviceId = winner.getDeviceId();
            Vehicle v = existingVehicles.get(deviceId);
            String plate = v != null ? v.getLicensePlate() : "?";
            String storedProvider = v != null && v.getGpsProvider() != null ? v.getGpsProvider().getCode() : null;
            String newProvider = winner.getGpsProvider() != null ? winner.getGpsProvider().getCode() : null;
            if (storedProvider != null && newProvider != null && !storedProvider.equals(newProvider)) {
                log.info("[GPS_PIPELINE] GPS_PROVIDER_SWITCH device={} plate={} from={} to={} newLat={} newLon={}",
                        deviceId, plate, storedProvider, newProvider,
                        winner.getLatitude(), winner.getLongitude());
            }
        }

        log.debug("[GPS_PIPELINE] PROCESS_BATCH input={} unique_vehicles={} cross_provider_duplicates={}",
                validPositions.size(), latestPositionsByDevice.size(), duplicatesByDevice.size());

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

                    boolean frozenCoordsWithMotion = hasSignificantChange
                            && Boolean.TRUE.equals(updatedVehicle.getIsInMotion())
                            && oldLatitude != null && oldLongitude != null
                            && !positionChangeDetector.hasSignificantPositionChange(
                                    oldLatitude, oldLongitude,
                                    updatedVehicle.getCurrentLatitude(),
                                    updatedVehicle.getCurrentLongitude());

                    if (frozenCoordsWithMotion) {
                        log.warn("[GPS_ANOMALY|SOURCE:SERVER] FROZEN_COORDS_WITH_MOTION: " +
                                        "device={}, plate={}, speed={}km/h, coords=({},{}) — suppressing WS publish",
                                gpsPosition.getDeviceId(),
                                updatedVehicle.getLicensePlate(),
                                String.format("%.1f", updatedVehicle.getSpeedKmh()),
                                String.format("%.6f", updatedVehicle.getCurrentLatitude()),
                                String.format("%.6f", updatedVehicle.getCurrentLongitude()));
                        if (gpsPosition.getDeviceId() != null) {
                            frozenCoordsDeviceIds.add(gpsPosition.getDeviceId());
                        }
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

        Mono<List<Vehicle>> vehiclesWithDirections = vehiclesToUpdate.isEmpty() ?
                Mono.just(List.of()) :
                directionDetectionService.updateVehicleDirectionsBatch(vehiclesForDetection)
                        .map(detectedVehicles -> {
                            Map<String, Vehicle> detectedById = detectedVehicles.stream()
                                    .collect(java.util.stream.Collectors.toMap(
                                            v -> v.getId().getValue(), v -> v));
                            return vehiclesToUpdate.stream()
                                    .map(v -> {
                                        Vehicle detected = detectedById.get(v.getId().getValue());
                                        if (detected != null && detected.getCurrentDirection() != null) {
                                            return v.updateDirection(detected.getLastStopSequence(),
                                                    detected.getCurrentDirection());
                                        }
                                        return v;
                                    })
                                    .collect(java.util.stream.Collectors.toList());
                        })
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
            for (Vehicle v : updatedVehicles) {
                if (v.getCurrentLatitude() == null || v.getCurrentLongitude() == null) {
                    continue;
                }
                if (Boolean.TRUE.equals(frozenCoordsWithMotionById.get(v.getId().getValue()))) {
                    log.debug("[GPS_PIPELINE] FROZEN_FIX_SKIP_PREDICTION vehicle={} plate={} — prediction advance + history suppressed",
                            v.getId().getValue(), v.getLicensePlate());
                    continue;
                }
                predictionService.onGpsUpdate(
                        v.getId().getValue(),
                        v.getLicensePlate(),
                        v.getRouteNumber(),
                        v.getCurrentLatitude(),
                        v.getCurrentLongitude(),
                        v.getSpeedKmh() != null ? v.getSpeedKmh() : 0.0,
                        (v.getCourse() != null && v.getCourse() > 0.0)
                                ? v.getCourse()
                                : estimatedBearings.getOrDefault(v.getId().getValue(), 0.0),
                        Boolean.TRUE.equals(v.getIsInMotion()),
                        v.getLastPositionUpdate() != null
                                ? v.getLastPositionUpdate().toInstant(ZoneOffset.UTC)
                                : Instant.now(),
                        v.getCurrentDirection() != null ? v.getCurrentDirection() : 0,
                        Boolean.TRUE.equals(v.getIsInGarage()),
                        Boolean.TRUE.equals(bufferedByDeviceId.get(v.getDeviceId()))
                );
            }

            List<Vehicle> gatedVehicles = updatedVehicles.stream()
                    .map(v -> applyGatekeeperDecision(v, oldCoordsByVehicleId))
                    .toList();

            for (Vehicle v : gatedVehicles) {
                publishPositionEvent(v,
                        hasSignificantChangeById.getOrDefault(v.getId().getValue(), false),
                        frozenCoordsWithMotionById.getOrDefault(v.getId().getValue(), false));
            }

            Map<String, Integer> directionFixes = predictionService.drainPendingDirectionFixes();
            Mono<Integer> dirFixMono = directionFixes.isEmpty()
                    ? Mono.just(0)
                    : vehicleRepository.batchUpdateDirections(directionFixes)
                            .doOnNext(n -> log.debug("[GPS_PIPELINE] DIR_AUTO_FIX applied {} direction corrections", n));

            Mono<Integer> updateMono = gatedVehicles.isEmpty() ?
                    Mono.just(0) : vehicleRepository.batchUpdate(gatedVehicles);

            Mono<List<Vehicle>> insertMono = vehiclesToCreate.isEmpty() ?
                    Mono.just(List.of()) : vehicleRepository.batchInsert(vehiclesToCreate).collectList();

            return Mono.zip(updateMono, insertMono, dirFixMono)
                    .flatMap(tuple -> {
                        long rawWinnersWithPosition = latestPositionsByDevice.values().stream()
                                .filter(p -> p.getLatitude() != null && p.getLongitude() != null)
                                .count();

                        if (rawWinnersWithPosition == 0) {
                            return Mono.just(tuple);
                        }

                        return Flux.fromIterable(latestPositionsByDevice.values())
                                .filter(p -> p.getLatitude() != null && p.getLongitude() != null)
                                .filter(p -> !frozenCoordsDeviceIds.contains(p.getDeviceId()))
                                .flatMap(p -> gpsHistoryService.addPoint(
                                        p.getDeviceId(),
                                        p.getLatitude(),
                                        p.getLongitude(),
                                        p.getSpeed(),
                                        p.getFixTime()
                                ), 5)
                                .then(Mono.defer(() -> {
                                    log.debug("[GPS_PIPELINE] Saved raw GPS history for {} winners (frozen-skipped={})",
                                            rawWinnersWithPosition - frozenCoordsDeviceIds.size(),
                                            frozenCoordsDeviceIds.size());
                                    return Mono.just(tuple);
                                }));
                    })
                    .map(tuple -> {
                        log.debug("Batch operations: {} updated, {} created, {} direction fixes",
                                tuple.getT1(), tuple.getT2().size(), tuple.getT3());
                        return createResult(statuses);
                    });
        });
    }


    private void publishPositionEvent(Vehicle v, boolean hasSignificantChange, boolean frozenCoordsWithMotion) {
        String vehicleId = v.getId().getValue();
        GatekeeperDecision decision = predictionService.evaluateGate(vehicleId);
        boolean forcePublish = shouldForcePublishForVehicle(vehicleId);
        boolean shouldPublish = (hasSignificantChange || forcePublish) && !frozenCoordsWithMotion;

        if (!shouldPublish) {
            return;
        }

        lastPublishedTime.put(vehicleId, Instant.now());
        log.debug("[GPS_PIPELINE] WS_PUBLISH vehicle={} plate={} decision={} lat={} lon={} speed={} moved={}",
                vehicleId, v.getLicensePlate(), decision,
                v.getCurrentLatitude(), v.getCurrentLongitude(),
                v.getSpeedKmh(), hasSignificantChange);

        VehiclePositionUpdatedEvent event = new VehiclePositionUpdatedEvent(
                vehicleId,
                v.getDeviceId(),
                v.getLicensePlate(),
                v.getRouteNumber(),
                v.getCurrentLatitude(),
                v.getCurrentLongitude(),
                v.getSpeedKmh(),
                v.getIsInMotion(),
                v.getLastPositionUpdate(),
                v.getCourse(),
                v.getCurrentDirection()
        );
        domainEventPublisher.publish(event);
    }

    private Vehicle applyGatekeeperDecision(Vehicle v, Map<String, double[]> oldCoordsByVehicleId) {
        String vehicleId = v.getId().getValue();
        GatekeeperDecision decision = predictionService.evaluateGate(vehicleId);
        if (decision.allowsCoordinateWrite()) {
            return v;
        }
        double[] oldCoords = oldCoordsByVehicleId.get(vehicleId);
        if (oldCoords == null) {
            return v;
        }
        log.debug("[GPS_PIPELINE] DB_HEARTBEAT_ONLY vehicle={} plate={} decision={} — preserving coords, updating timing only",
                vehicleId, v.getLicensePlate(), decision);
        return v.toBuilder()
                .currentLatitude(oldCoords[0])
                .currentLongitude(oldCoords[1])
                .build();
    }

    private boolean shouldForcePublishForVehicle(String vehicleId) {
        if (predictionService.isActivelyPredicting(vehicleId)) {
            return false;
        }
        Instant lastPublished = lastPublishedTime.get(vehicleId);
        if (lastPublished == null) {
            return true;
        }
        long secondsSinceLastPublish = Instant.now().getEpochSecond() - lastPublished.getEpochSecond();
        return secondsSinceLastPublish >= FORCE_PUBLISH_INTERVAL_SECONDS;
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60_000)
    void cleanupStalePublishTimes() {
        Instant cutoff = Instant.now().minusSeconds(300);
        int before = lastPublishedTime.size();
        lastPublishedTime.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        int after = lastPublishedTime.size();
        if (before != after) {
            log.debug("[GPS_PIPELINE] lastPublishedTime cleanup: removed {} entries ({} -> {})",
                    before - after, before, after);
        }
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 60_000)
    void cleanupStaleIdempotencyEntries() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(IDEMPOTENCY_TTL_SECONDS);
        int before = lastProcessedFixTimeByDevice.size();
        lastProcessedFixTimeByDevice.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        int after = lastProcessedFixTimeByDevice.size();
        if (before != after) {
            log.debug("[GPS_PIPELINE] idempotency cleanup: removed {} entries ({} -> {})",
                    before - after, before, after);
        }
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