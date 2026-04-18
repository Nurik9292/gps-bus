package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
class GpsOutlierFilter {

    private static final double MAX_BUS_SPEED_MS = 33.0;
    private static final double OUTLIER_TOLERANCE = 1.0;
    private static final double MAX_TELEPORT_DISTANCE_METERS = 5_000.0;
    private static final double HARD_OUTLIER_METERS = 10_000.0;
    private static final long HARD_OUTLIER_WINDOW_MS = 10_000L;
    private static final long SOFT_OUTLIER_WINDOW_MS = 300_000L;

    enum Decision {
        ACCEPT,
        REJECT_HARD_OUTLIER,
        REJECT_SOFT_OUTLIER,
        REJECT_TELEPORT_GAP
    }

    Decision evaluate(VehiclePredictionState existing,
                      double latitude,
                      double longitude,
                      Instant timestamp,
                      String vehicleId,
                      String licensePlate) {
        if (existing == null) {
            return Decision.ACCEPT;
        }

        long elapsedMs = timestamp.toEpochMilli() - existing.getLastGpsUpdate().toEpochMilli();
        if (elapsedMs <= 0) {
            return Decision.ACCEPT;
        }

        double distFromLastGps = DistanceCalculationService.haversineDistanceMeters(
                existing.getGpsLatitude(), existing.getGpsLongitude(), latitude, longitude);

        if (elapsedMs < HARD_OUTLIER_WINDOW_MS && distFromLastGps > HARD_OUTLIER_METERS) {
            log.warn("[GPS_PIPELINE] HARD_OUTLIER_REJECTED vehicle={} plate={}: {}m in {}ms " +
                            "(implied {}km/h) — baseline preserved",
                    vehicleId, licensePlate,
                    (int) distFromLastGps, elapsedMs,
                    (int) (distFromLastGps / (elapsedMs / 1000.0) * 3.6));
            return Decision.REJECT_HARD_OUTLIER;
        }

        if (elapsedMs < SOFT_OUTLIER_WINDOW_MS) {
            double maxPossibleDist = (elapsedMs / 1000.0) * MAX_BUS_SPEED_MS * OUTLIER_TOLERANCE;
            if (distFromLastGps > maxPossibleDist) {
                log.warn("GPS outlier rejected for vehicle {}: {}m in {}ms (max {}m at {}km/h×{}) " +
                                "— baseline preserved",
                        vehicleId, (int) distFromLastGps, elapsedMs,
                        (int) maxPossibleDist, (int) (MAX_BUS_SPEED_MS * 3.6), OUTLIER_TOLERANCE);
                return Decision.REJECT_SOFT_OUTLIER;
            }
            return Decision.ACCEPT;
        }

        if (distFromLastGps > MAX_TELEPORT_DISTANCE_METERS) {
            log.warn("GPS teleportation rejected for vehicle {} after {}min gap: {}m (max {}m)",
                    vehicleId, elapsedMs / 60_000, (int) distFromLastGps,
                    (int) MAX_TELEPORT_DISTANCE_METERS);
            return Decision.REJECT_TELEPORT_GAP;
        }

        return Decision.ACCEPT;
    }
}
