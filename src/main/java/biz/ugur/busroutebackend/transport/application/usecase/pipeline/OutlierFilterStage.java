package biz.ugur.busroutebackend.transport.application.usecase.pipeline;

import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.service.GpsOutlierDetector;
import biz.ugur.busroutebackend.transport.domain.valueobject.OutlierDetectionResult;
import biz.ugur.busroutebackend.transport.infrastructure.config.GpsOutlierDetectionProperties;
import biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer;
import biz.ugur.busroutebackend.transport.infrastructure.metrics.GpsOutlierMetricsRecorder;
import biz.ugur.busroutebackend.transport.infrastructure.redis.GpsPoint;
import biz.ugur.busroutebackend.transport.infrastructure.redis.VehicleGpsHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
@Slf4j
public class OutlierFilterStage {

    private final GpsOutlierDetector outlierDetector;
    private final GpsOutlierMetricsRecorder outlierMetricsRecorder;
    private final GpsOutlierDetectionProperties outlierDetectionProperties;
    private final VehicleGpsHistoryService gpsHistoryService;
    private final VehicleRepository vehicleRepository;
    private final PipelineTracer pipelineTracer;

    public OutlierFilterStage(GpsOutlierDetector outlierDetector,
                               GpsOutlierMetricsRecorder outlierMetricsRecorder,
                               GpsOutlierDetectionProperties outlierDetectionProperties,
                               VehicleGpsHistoryService gpsHistoryService,
                               VehicleRepository vehicleRepository,
                               PipelineTracer pipelineTracer) {
        this.outlierDetector = outlierDetector;
        this.outlierMetricsRecorder = outlierMetricsRecorder;
        this.outlierDetectionProperties = outlierDetectionProperties;
        this.gpsHistoryService = gpsHistoryService;
        this.vehicleRepository = vehicleRepository;
        this.pipelineTracer = pipelineTracer;
    }

    private record DbBaselinePoint(double latitude, double longitude, LocalDateTime timestamp)
            implements GpsOutlierDetector.GpsHistoryPoint {
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    private record PositionWithOutlierResult(GpsPositionDTO position, OutlierDetectionResult detectionResult) {}

    public Mono<List<GpsPositionDTO>> apply(List<GpsPositionDTO> positions) {
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
                .flatMap(historyByDevice -> resolveBaselines(deviceIds, historyByDevice))
                .flatMap(baselineByDevice -> Flux.fromIterable(positions)
                        .map(position -> {
                            var history = baselineByDevice.getOrDefault(position.getDeviceId(),
                                    List.<GpsOutlierDetector.GpsHistoryPoint>of());
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

                        pipelineTracer.traceOutlierDecision(
                                r.detectionResult().deviceId(),
                                null,
                                type.name(),
                                r.detectionResult().distanceMeters(),
                                r.detectionResult().timeDifferenceSeconds() * 1000L,
                                r.detectionResult().maxAllowedSpeedKmh());

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

    private Mono<Map<String, List<? extends GpsOutlierDetector.GpsHistoryPoint>>> resolveBaselines(
            List<String> deviceIds, Map<String, List<GpsPoint>> historyByDevice) {
        List<String> noHistoryDevices = deviceIds.stream()
                .filter(deviceId -> historyByDevice.getOrDefault(deviceId, List.of()).isEmpty())
                .toList();
        Mono<Map<String, Vehicle>> dbVehicles = noHistoryDevices.isEmpty()
                ? Mono.just(Map.of())
                : vehicleRepository.findByDeviceIds(noHistoryDevices);
        return dbVehicles.map(vehicles -> {
            Map<String, List<? extends GpsOutlierDetector.GpsHistoryPoint>> baselines = new HashMap<>();
            for (String deviceId : deviceIds) {
                List<GpsPoint> redisHistory = historyByDevice.getOrDefault(deviceId, List.of());
                if (!redisHistory.isEmpty()) {
                    baselines.put(deviceId, redisHistory);
                } else {
                    DbBaselinePoint dbBaseline = dbBaselineFrom(vehicles.get(deviceId));
                    baselines.put(deviceId, dbBaseline != null ? List.of(dbBaseline) : List.of());
                }
            }
            return baselines;
        });
    }

    private DbBaselinePoint dbBaselineFrom(Vehicle vehicle) {
        if (vehicle == null || vehicle.getCurrentLatitude() == null
                || vehicle.getCurrentLongitude() == null || vehicle.getLastPositionUpdate() == null) {
            return null;
        }
        return new DbBaselinePoint(vehicle.getCurrentLatitude(), vehicle.getCurrentLongitude(),
                vehicle.getLastPositionUpdate());
    }
}
