package biz.ugur.busroutebackend.transport.domain.valueobject;

import java.time.LocalDateTime;

public record OutlierDetectionResult(
        boolean isOutlier,

        OutlierType type,

        double impliedSpeedKmh,

        double distanceMeters,

        long timeDifferenceSeconds,

        double maxAllowedSpeedKmh,

        String deviceId,

        LocalDateTime detectedAt
) {

    public enum OutlierType {
        VALID,

        SPEED_EXCEEDED,

        /**
         * GPS coordinates frozen (distance ≈ 0) while reported speed > threshold.
         * Indicates stale GPS fix from provider — CAN-bus speed is live but position is not.
         */
        FROZEN_COORDINATES_WITH_MOTION,

        NO_HISTORY,

        TIME_GAP_TOO_LARGE,

        TIME_GAP_TOO_SMALL,

        DISTANCE_TOO_SMALL,

        DETECTION_DISABLED
    }

    public static OutlierDetectionResult valid(String deviceId, double impliedSpeedKmh,
                                                double distanceMeters, long timeDifferenceSeconds,
                                                double maxAllowedSpeedKmh) {
        return new OutlierDetectionResult(
                false,
                OutlierType.VALID,
                impliedSpeedKmh,
                distanceMeters,
                timeDifferenceSeconds,
                maxAllowedSpeedKmh,
                deviceId,
                LocalDateTime.now()
        );
    }

    public static OutlierDetectionResult outlier(String deviceId, double impliedSpeedKmh,
                                                  double distanceMeters, long timeDifferenceSeconds,
                                                  double maxAllowedSpeedKmh) {
        return new OutlierDetectionResult(
                true,
                OutlierType.SPEED_EXCEEDED,
                impliedSpeedKmh,
                distanceMeters,
                timeDifferenceSeconds,
                maxAllowedSpeedKmh,
                deviceId,
                LocalDateTime.now()
        );
    }

    public static OutlierDetectionResult noHistory(String deviceId, double maxAllowedSpeedKmh) {
        return new OutlierDetectionResult(
                false,
                OutlierType.NO_HISTORY,
                0.0,
                0.0,
                0,
                maxAllowedSpeedKmh,
                deviceId,
                LocalDateTime.now()
        );
    }

    public static OutlierDetectionResult timeGapTooLarge(String deviceId, long timeDifferenceSeconds,
                                                          double maxAllowedSpeedKmh) {
        return new OutlierDetectionResult(
                false,
                OutlierType.TIME_GAP_TOO_LARGE,
                0.0,
                0.0,
                timeDifferenceSeconds,
                maxAllowedSpeedKmh,
                deviceId,
                LocalDateTime.now()
        );
    }

    public static OutlierDetectionResult timeGapTooSmall(String deviceId, long timeDifferenceSeconds,
                                                          double maxAllowedSpeedKmh) {
        return new OutlierDetectionResult(
                false,
                OutlierType.TIME_GAP_TOO_SMALL,
                0.0,
                0.0,
                timeDifferenceSeconds,
                maxAllowedSpeedKmh,
                deviceId,
                LocalDateTime.now()
        );
    }

    public static OutlierDetectionResult distanceTooSmall(String deviceId, double distanceMeters,
                                                           double maxAllowedSpeedKmh) {
        return new OutlierDetectionResult(
                false,
                OutlierType.DISTANCE_TOO_SMALL,
                0.0,
                distanceMeters,
                0,
                maxAllowedSpeedKmh,
                deviceId,
                LocalDateTime.now()
        );
    }

    public static OutlierDetectionResult frozenCoordinatesWithMotion(String deviceId, double distanceMeters,
                                                                      double reportedSpeedKmh) {
        return new OutlierDetectionResult(
                true,
                OutlierType.FROZEN_COORDINATES_WITH_MOTION,
                0.0,
                distanceMeters,
                0,
                reportedSpeedKmh,
                deviceId,
                LocalDateTime.now()
        );
    }

    public static OutlierDetectionResult disabled(String deviceId) {
        return new OutlierDetectionResult(
                false,
                OutlierType.DETECTION_DISABLED,
                0.0,
                0.0,
                0,
                0.0,
                deviceId,
                LocalDateTime.now()
        );
    }

    public boolean wasEvaluated() {
        return type == OutlierType.VALID || type == OutlierType.SPEED_EXCEEDED
                || type == OutlierType.FROZEN_COORDINATES_WITH_MOTION;
    }

    public String getDescription() {
        return switch (type) {
            case VALID -> String.format("Valid position (%.1f km/h implied, max %.1f km/h)",
                    impliedSpeedKmh, maxAllowedSpeedKmh);
            case SPEED_EXCEEDED -> String.format("OUTLIER: %.1f km/h implied exceeds max %.1f km/h (%.0fm in %ds)",
                    impliedSpeedKmh, maxAllowedSpeedKmh, distanceMeters, timeDifferenceSeconds);
            case FROZEN_COORDINATES_WITH_MOTION -> String.format(
                    "FROZEN: coordinates unchanged (%.1fm) but reported speed %.1f km/h — stale GPS fix",
                    distanceMeters, maxAllowedSpeedKmh);
            case NO_HISTORY -> "No historical position available";
            case TIME_GAP_TOO_LARGE -> String.format("Time gap too large (%ds)", timeDifferenceSeconds);
            case TIME_GAP_TOO_SMALL -> String.format("Time gap too small (%ds)", timeDifferenceSeconds);
            case DISTANCE_TOO_SMALL -> String.format("Distance too small (%.1fm)", distanceMeters);
            case DETECTION_DISABLED -> "Detection disabled";
        };
    }
}
