package biz.ugur.busroutebackend.transport.application.usecase.pipeline;

import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.service.GpsOutlierDetector;
import biz.ugur.busroutebackend.transport.domain.valueobject.OutlierDetectionResult;
import biz.ugur.busroutebackend.transport.infrastructure.config.GpsOutlierDetectionProperties;
import biz.ugur.busroutebackend.transport.infrastructure.metrics.GpsOutlierMetricsRecorder;
import biz.ugur.busroutebackend.transport.infrastructure.redis.VehicleGpsHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@Slf4j
public class OutlierFilterStage {

    private final GpsOutlierDetector outlierDetector;
    private final GpsOutlierMetricsRecorder outlierMetricsRecorder;
    private final GpsOutlierDetectionProperties outlierDetectionProperties;
    private final VehicleGpsHistoryService gpsHistoryService;

    public OutlierFilterStage(GpsOutlierDetector outlierDetector,
                               GpsOutlierMetricsRecorder outlierMetricsRecorder,
                               GpsOutlierDetectionProperties outlierDetectionProperties,
                               VehicleGpsHistoryService gpsHistoryService) {
        this.outlierDetector = outlierDetector;
        this.outlierMetricsRecorder = outlierMetricsRecorder;
        this.outlierDetectionProperties = outlierDetectionProperties;
        this.gpsHistoryService = gpsHistoryService;
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
}
