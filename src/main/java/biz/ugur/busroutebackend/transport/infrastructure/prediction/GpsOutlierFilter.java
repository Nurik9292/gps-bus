package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
class GpsOutlierFilter {

    private final PredictionProperties properties;

    GpsOutlierFilter(PredictionProperties properties) {
        this.properties = properties;
    }

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

        if (elapsedMs < properties.getHardOutlierWindowMs() && distFromLastGps > properties.getHardOutlierMeters()) {
            log.warn("[GPS_PIPELINE] HARD_OUTLIER_REJECTED vehicle={} plate={}: {}m in {}ms " +
                            "(implied {}km/h) — baseline preserved",
                    vehicleId, licensePlate,
                    (int) distFromLastGps, elapsedMs,
                    (int) (distFromLastGps / (elapsedMs / 1000.0) * 3.6));
            return Decision.REJECT_HARD_OUTLIER;
        }

        if (elapsedMs < properties.getSoftOutlierWindowMs()) {
            double maxPossibleDist = (elapsedMs / 1000.0) * properties.getMaxBusSpeedMs() * properties.getOutlierTolerance();
            if (distFromLastGps > maxPossibleDist) {
                log.warn("GPS outlier rejected for vehicle {}: {}m in {}ms (max {}m at {}km/h×{}) " +
                                "— baseline preserved",
                        vehicleId, (int) distFromLastGps, elapsedMs,
                        (int) maxPossibleDist, (int) (properties.getMaxBusSpeedMs() * 3.6),
                        properties.getOutlierTolerance());
                return Decision.REJECT_SOFT_OUTLIER;
            }
            return Decision.ACCEPT;
        }

        if (distFromLastGps > properties.getMaxTeleportDistanceMeters()) {
            log.warn("GPS teleportation rejected for vehicle {} after {}min gap: {}m (max {}m)",
                    vehicleId, elapsedMs / 60_000, (int) distFromLastGps,
                    (int) properties.getMaxTeleportDistanceMeters());
            return Decision.REJECT_TELEPORT_GAP;
        }

        return Decision.ACCEPT;
    }
}
