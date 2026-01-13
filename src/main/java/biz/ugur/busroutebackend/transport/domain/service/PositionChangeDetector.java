package biz.ugur.busroutebackend.transport.domain.service;

import lombok.extern.slf4j.Slf4j;


@Slf4j
public class PositionChangeDetector {

    private static final double DEFAULT_POSITION_DELTA_THRESHOLD = 0.00005;
    private static final double DEFAULT_SPEED_DELTA_THRESHOLD_KMH = 2.0;
    private static final double DEFAULT_MIN_SPEED_FOR_MOTION_KMH = 3.0;

    private final double positionDeltaThreshold;
    private final double speedDeltaThresholdKmh;
    private final double minSpeedForMotionKmh;

    public PositionChangeDetector() {
        this(DEFAULT_POSITION_DELTA_THRESHOLD, DEFAULT_SPEED_DELTA_THRESHOLD_KMH, DEFAULT_MIN_SPEED_FOR_MOTION_KMH);
    }


    public PositionChangeDetector(double positionDeltaThreshold,
                                   double speedDeltaThresholdKmh,
                                   double minSpeedForMotionKmh) {
        this.positionDeltaThreshold = positionDeltaThreshold;
        this.speedDeltaThresholdKmh = speedDeltaThresholdKmh;
        this.minSpeedForMotionKmh = minSpeedForMotionKmh;

        log.info("PositionChangeDetector initialized with thresholds: " +
                 "positionDelta={} (~{}m), speedDelta={} km/h, minSpeedForMotion={} km/h",
                positionDeltaThreshold,
                Math.round(positionDeltaThreshold * 111_000),
                speedDeltaThresholdKmh,
                minSpeedForMotionKmh);
    }

    public boolean hasSignificantChange(Double oldLatitude, Double oldLongitude, Double oldSpeed,
                                        Double newLatitude, Double newLongitude, Double newSpeed) {
        if (oldLatitude == null || oldLongitude == null) {
            log.trace("First position update detected (no previous position)");
            return true;
        }

        boolean positionChanged = hasSignificantPositionChange(
                oldLatitude, oldLongitude, newLatitude, newLongitude
        );

        boolean speedChanged = hasSignificantSpeedChange(oldSpeed, newSpeed);

        log.trace("Position change check: posChanged={}, speedChanged={}, " +
                        "deltaLat={}, deltaLon={}, speedDelta={}",
                positionChanged, speedChanged,
                Math.abs(newLatitude - oldLatitude),
                Math.abs(newLongitude - oldLongitude),
                (oldSpeed != null && newSpeed != null) ? Math.abs(newSpeed - oldSpeed) : null
        );

        return positionChanged || speedChanged;
    }


    public boolean hasSignificantPositionChange(Double oldLatitude, Double oldLongitude,
                                                 Double newLatitude, Double newLongitude) {
        if (oldLatitude == null || oldLongitude == null || newLatitude == null || newLongitude == null) {
            return false;
        }

        double deltaLat = Math.abs(newLatitude - oldLatitude);
        double deltaLon = Math.abs(newLongitude - oldLongitude);

        return deltaLat > positionDeltaThreshold || deltaLon > positionDeltaThreshold;
    }

    public boolean hasSignificantSpeedChange(Double oldSpeed, Double newSpeed) {
        if (oldSpeed == null || newSpeed == null) {
            return false;
        }

        double speedDelta = Math.abs(newSpeed - oldSpeed);
        return speedDelta > speedDeltaThresholdKmh;
    }


    public boolean isInMotion(Double speed) {
        if (speed == null) {
            return false;
        }
        return speed >= minSpeedForMotionKmh;
    }

    public Thresholds getThresholds() {
        return new Thresholds(positionDeltaThreshold, speedDeltaThresholdKmh, minSpeedForMotionKmh);
    }


    public record Thresholds(
            double positionDeltaThreshold,
            double speedDeltaThresholdKmh,
            double minSpeedForMotionKmh
    ) {
        public double positionDeltaMeters() {
            return positionDeltaThreshold * 111_000;
        }
    }
}
