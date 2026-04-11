package biz.ugur.busroutebackend.transport.domain.service;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.transport.domain.valueobject.OutlierDetectionResult;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
public class GpsOutlierDetector {

    private static final double METERS_PER_SECOND_TO_KMH = 3.6;

    private static final double DEFAULT_MAX_IMPLIED_SPEED_KMH = 150.0;
    private static final long DEFAULT_MIN_TIME_DIFF_SECONDS = 3;
    private static final long DEFAULT_MAX_TIME_DIFF_SECONDS = 60;
    private static final double DEFAULT_MIN_DISTANCE_METERS = 10.0;

    private static final double DEFAULT_MIN_SPEED_FOR_FROZEN_DETECTION_KMH = 10.0;

    private final boolean enabled;
    private final double maxImpliedSpeedKmh;
    private final long minTimeDifferenceSeconds;
    private final long maxTimeDifferenceSeconds;
    private final double minDistanceMeters;
    private final double minSpeedForFrozenDetectionKmh;

    public GpsOutlierDetector() {
        this(true, DEFAULT_MAX_IMPLIED_SPEED_KMH, DEFAULT_MIN_TIME_DIFF_SECONDS,
                DEFAULT_MAX_TIME_DIFF_SECONDS, DEFAULT_MIN_DISTANCE_METERS,
                DEFAULT_MIN_SPEED_FOR_FROZEN_DETECTION_KMH);
    }

    public GpsOutlierDetector(boolean enabled, double maxImpliedSpeedKmh,
                               long minTimeDifferenceSeconds, long maxTimeDifferenceSeconds,
                               double minDistanceMeters) {
        this(enabled, maxImpliedSpeedKmh, minTimeDifferenceSeconds, maxTimeDifferenceSeconds,
                minDistanceMeters, DEFAULT_MIN_SPEED_FOR_FROZEN_DETECTION_KMH);
    }

    public GpsOutlierDetector(boolean enabled, double maxImpliedSpeedKmh,
                               long minTimeDifferenceSeconds, long maxTimeDifferenceSeconds,
                               double minDistanceMeters, double minSpeedForFrozenDetectionKmh) {
        this.enabled = enabled;
        this.maxImpliedSpeedKmh = maxImpliedSpeedKmh;
        this.minTimeDifferenceSeconds = minTimeDifferenceSeconds;
        this.maxTimeDifferenceSeconds = maxTimeDifferenceSeconds;
        this.minDistanceMeters = minDistanceMeters;
        this.minSpeedForFrozenDetectionKmh = minSpeedForFrozenDetectionKmh;

        log.info("GpsOutlierDetector initialized: enabled={}, maxImpliedSpeed={} km/h, " +
                        "timeDiffRange=[{}-{}]s, minDistance={}m, frozenDetectionThreshold={}km/h",
                enabled, maxImpliedSpeedKmh, minTimeDifferenceSeconds,
                maxTimeDifferenceSeconds, minDistanceMeters, minSpeedForFrozenDetectionKmh);
    }

    public OutlierDetectionResult detect(String deviceId,
                                          double newLatitude, double newLongitude, LocalDateTime newTimestamp,
                                          double previousLatitude, double previousLongitude, LocalDateTime previousTimestamp) {

        if (!enabled) {
            return OutlierDetectionResult.disabled(deviceId);
        }

        if (previousTimestamp == null || newTimestamp == null) {
            return OutlierDetectionResult.noHistory(deviceId, maxImpliedSpeedKmh);
        }

        long timeDiffSeconds = Duration.between(previousTimestamp, newTimestamp).getSeconds();

        if (timeDiffSeconds < minTimeDifferenceSeconds) {
            log.trace("Time gap too small for device {}: {}s < {}s",
                    deviceId, timeDiffSeconds, minTimeDifferenceSeconds);
            return OutlierDetectionResult.timeGapTooSmall(deviceId, timeDiffSeconds, maxImpliedSpeedKmh);
        }

        if (timeDiffSeconds > maxTimeDifferenceSeconds) {
            log.trace("Time gap too large for device {}: {}s > {}s",
                    deviceId, timeDiffSeconds, maxTimeDifferenceSeconds);
            return OutlierDetectionResult.timeGapTooLarge(deviceId, timeDiffSeconds, maxImpliedSpeedKmh);
        }

        double distanceMeters = DistanceCalculationService.haversineDistanceMeters(
                previousLatitude, previousLongitude,
                newLatitude, newLongitude
        );

        if (distanceMeters < minDistanceMeters) {
            log.trace("Distance too small for device {}: {}m < {}m",
                    deviceId, String.format("%.1f", distanceMeters), minDistanceMeters);
            return OutlierDetectionResult.distanceTooSmall(deviceId, distanceMeters, maxImpliedSpeedKmh);
        }


        double speedMps = distanceMeters / timeDiffSeconds;
        double impliedSpeedKmh = speedMps * METERS_PER_SECOND_TO_KMH;

        if (impliedSpeedKmh > maxImpliedSpeedKmh) {
            log.warn("OUTLIER detected for device {}: implied speed {} km/h exceeds max {} km/h " +
                            "(distance: {}m, time: {}s)",
                    deviceId, String.format("%.1f", impliedSpeedKmh),
                    String.format("%.1f", maxImpliedSpeedKmh),
                    String.format("%.0f", distanceMeters), timeDiffSeconds);

            return OutlierDetectionResult.outlier(deviceId, impliedSpeedKmh,
                    distanceMeters, timeDiffSeconds, maxImpliedSpeedKmh);
        }

        log.trace("Position valid for device {}: implied speed {} km/h (distance: {}m, time: {}s)",
                deviceId, String.format("%.1f", impliedSpeedKmh),
                String.format("%.0f", distanceMeters), timeDiffSeconds);

        return OutlierDetectionResult.valid(deviceId, impliedSpeedKmh,
                distanceMeters, timeDiffSeconds, maxImpliedSpeedKmh);
    }

    public <T extends GpsHistoryPoint> OutlierDetectionResult detectWithHistory(
            String deviceId,
            double newLatitude, double newLongitude, LocalDateTime newTimestamp,
            List<T> historyPoints) {
        return detectWithHistory(deviceId, newLatitude, newLongitude, newTimestamp, historyPoints, null);
    }

    public <T extends GpsHistoryPoint> OutlierDetectionResult detectWithHistory(
            String deviceId,
            double newLatitude, double newLongitude, LocalDateTime newTimestamp,
            List<T> historyPoints,
            Double reportedSpeedKmh) {

        if (!enabled) {
            return OutlierDetectionResult.disabled(deviceId);
        }

        if (historyPoints == null || historyPoints.isEmpty()) {
            return OutlierDetectionResult.noHistory(deviceId, maxImpliedSpeedKmh);
        }

        T mostRecentPoint = historyPoints.getFirst();

        OutlierDetectionResult baseResult = detect(deviceId,
                newLatitude, newLongitude, newTimestamp,
                mostRecentPoint.getLatitude(), mostRecentPoint.getLongitude(), mostRecentPoint.getTimestamp());

        if (baseResult.type() == OutlierDetectionResult.OutlierType.DISTANCE_TOO_SMALL
                && reportedSpeedKmh != null
                && reportedSpeedKmh >= minSpeedForFrozenDetectionKmh) {

            double distanceMeters = DistanceCalculationService.haversineDistanceMeters(
                    mostRecentPoint.getLatitude(), mostRecentPoint.getLongitude(),
                    newLatitude, newLongitude
            );

            log.warn("[GPS_ANOMALY|SOURCE:PROVIDER] FROZEN_COORDS_WITH_MOTION: " +
                            "device={}, distance={}m, reportedSpeed={}km/h — stale GPS fix",
                    deviceId, String.format("%.1f", distanceMeters),
                    String.format("%.1f", reportedSpeedKmh));

            return OutlierDetectionResult.frozenCoordinatesWithMotion(deviceId, distanceMeters, reportedSpeedKmh);
        }

        return baseResult;
    }

    public interface GpsHistoryPoint {
        double getLatitude();
        double getLongitude();
        LocalDateTime getTimestamp();
    }

    public Configuration getConfiguration() {
        return new Configuration(enabled, maxImpliedSpeedKmh, minTimeDifferenceSeconds,
                maxTimeDifferenceSeconds, minDistanceMeters);
    }

    public record Configuration(
            boolean enabled,
            double maxImpliedSpeedKmh,
            long minTimeDifferenceSeconds,
            long maxTimeDifferenceSeconds,
            double minDistanceMeters
    ) {}
}
