package biz.ugur.busroutebackend.transport.domain.service;

import lombok.extern.slf4j.Slf4j;


@Slf4j
public class PositionChangeDetector {

    private static final double POSITION_DELTA_THRESHOLD = 0.00005;
    private static final double SPEED_DELTA_THRESHOLD_KMH = 2.0;


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

        return deltaLat > POSITION_DELTA_THRESHOLD || deltaLon > POSITION_DELTA_THRESHOLD;
    }


    public boolean hasSignificantSpeedChange(Double oldSpeed, Double newSpeed) {
        if (oldSpeed == null || newSpeed == null) {
            return false;
        }

        double speedDelta = Math.abs(newSpeed - oldSpeed);
        return speedDelta > SPEED_DELTA_THRESHOLD_KMH;
    }
}
