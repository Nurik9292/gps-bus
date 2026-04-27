package biz.ugur.busroutebackend.transport.infrastructure.prediction.snap;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.MapMatchingService;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.PredictionProperties;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePredictionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PlausibilityChecker {

    private final PredictionProperties properties;

    public PlausibilityChecker(PredictionProperties properties) {
        this.properties = properties;
    }

    public boolean isDirectionFlipPhysicallyPlausible(String vehicleId,
                                                       String trigger,
                                                       VehiclePredictionState existing,
                                                       MapMatchingService.SnappedResult flippedSnap,
                                                       double curFraction) {
        if (existing == null
                || existing.getPredictedLatitude() == 0.0
                || existing.getPredictedLongitude() == 0.0) {
            return true;
        }

        double tolerance = properties.getTerminalFractionTolerance();

        boolean curNearTerminal = curFraction >= 0
                && (curFraction <= tolerance || curFraction >= (1.0 - tolerance));
        double predFrac = existing.getFractionOnRoute() >= 0
                ? existing.getFractionOnRoute()
                : existing.getLastGpsFraction();
        boolean predNearTerminal = predFrac >= 0
                && (predFrac <= tolerance || predFrac >= (1.0 - tolerance));

        double physicalJumpMeters = DistanceCalculationService.haversineDistanceMeters(
                existing.getPredictedLatitude(), existing.getPredictedLongitude(),
                flippedSnap.latitude(), flippedSnap.longitude());

        if (curNearTerminal || predNearTerminal) {
            if (physicalJumpMeters > properties.getTerminalFlipMaxPhysicalJumpMeters()) {
                log.warn("[GPS_PIPELINE] DIR_FLIP_REJECTED_TERMINAL_FAR vehicle={} trigger={} physicalJump={}m > {}m " +
                                "curFrac={} predFrac={} — at-terminal, but flipped snap is physically far (likely two close terminals on different routes)",
                        vehicleId, trigger,
                        String.format("%.0f", physicalJumpMeters),
                        (int) properties.getTerminalFlipMaxPhysicalJumpMeters(),
                        curFraction >= 0 ? String.format("%.4f", curFraction) : "-",
                        existing.getFractionOnRoute() >= 0 ? String.format("%.4f", existing.getFractionOnRoute()) : "-");
                return false;
            }
            log.debug("[GPS_PIPELINE] DIR_FLIP_ALLOWED_TERMINAL vehicle={} trigger={} curFrac={} predFrac={} physicalJump={}m",
                    vehicleId, trigger,
                    curFraction >= 0 ? String.format("%.4f", curFraction) : "-",
                    existing.getFractionOnRoute() >= 0 ? String.format("%.4f", existing.getFractionOnRoute()) : "-",
                    String.format("%.0f", physicalJumpMeters));
            return true;
        }

        if (physicalJumpMeters <= properties.getDirectionFlipMaxDistanceMeters()) {
            return true;
        }

        log.warn("[GPS_PIPELINE] DIR_FLIP_REJECTED vehicle={} trigger={} physicalJump={}m > max={}m " +
                        "curFrac={} predFrac={} — ignoring flip (not near terminal)",
                vehicleId, trigger,
                String.format("%.0f", physicalJumpMeters),
                String.format("%.0f", properties.getDirectionFlipMaxDistanceMeters()),
                curFraction >= 0 ? String.format("%.4f", curFraction) : "-",
                existing.getFractionOnRoute() >= 0 ? String.format("%.4f", existing.getFractionOnRoute()) : "-");
        return false;
    }
}
