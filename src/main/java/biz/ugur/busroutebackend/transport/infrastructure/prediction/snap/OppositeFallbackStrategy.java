package biz.ugur.busroutebackend.transport.infrastructure.prediction.snap;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.MapMatchingService;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.PredictionProperties;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.RouteGeometryCache;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePredictionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class OppositeFallbackStrategy {

    private final PredictionProperties properties;
    private final RouteGeometryCache routeGeometryCache;
    private final MapMatchingService mapMatchingService;
    private final PlausibilityChecker plausibilityChecker;
    private final ConsecutiveOppositeCounter oppositeCounter;

    public OppositeFallbackStrategy(PredictionProperties properties,
                                     RouteGeometryCache routeGeometryCache,
                                     MapMatchingService mapMatchingService,
                                     PlausibilityChecker plausibilityChecker,
                                     ConsecutiveOppositeCounter oppositeCounter) {
        this.properties = properties;
        this.routeGeometryCache = routeGeometryCache;
        this.mapMatchingService = mapMatchingService;
        this.plausibilityChecker = plausibilityChecker;
        this.oppositeCounter = oppositeCounter;
    }

    public record Result(
            boolean accepted,
            int direction,
            List<double[]> routeCoords,
            double totalDist,
            MapMatchingService.SnappedResult snap,
            double rawSnapMinDistance) {

        static Result notAccepted(int currentDirection, double rawSnapMinDistance) {
            return new Result(false, currentDirection, null, 0.0, null, rawSnapMinDistance);
        }

        static Result accepted(int newDirection,
                                List<double[]> routeCoords,
                                double totalDist,
                                MapMatchingService.SnappedResult snap,
                                double rawSnapMinDistance) {
            return new Result(true, newDirection, routeCoords, totalDist, snap, rawSnapMinDistance);
        }
    }

    public Result tryFlip(VehiclePredictionState existing,
                           String vehicleId, String licensePlate, String routeNumber,
                           int currentDirection, double latitude, double longitude,
                           MapMatchingService.SnappedResult primaryFailed,
                           double currentRawSnapMinDistance) {

        double rawSnapMinDistance = currentRawSnapMinDistance;
        int oppositeDir = (currentDirection == 0) ? 1 : 0;

        List<double[]> oppositeCoords = routeGeometryCache.getPoints(routeNumber, oppositeDir);
        if (oppositeCoords == null) {
            log.debug("[GPS_PIPELINE] SNAP_FAIL vehicle={} route={} dist={}m > threshold={}m → keeping predicted on route, awaiting re-snap",
                    vehicleId, routeNumber,
                    String.format("%.1f", primaryFailed.distanceMeters()),
                    (int) properties.getMaxSnapDistanceMeters());
            oppositeCounter.reset(vehicleId);
            return Result.notAccepted(currentDirection, rawSnapMinDistance);
        }

        double oppositeDist = routeGeometryCache.getTotalDistance(routeNumber, oppositeDir);
        MapMatchingService.SnappedResult oppositeSnap =
                mapMatchingService.snapToNearestSegment(latitude, longitude, oppositeCoords, oppositeDist);

        if (oppositeSnap.snapped()) {
            rawSnapMinDistance = Math.min(rawSnapMinDistance, oppositeSnap.distanceMeters());
        }

        boolean oppositePlausible = oppositeSnap.snapped()
                && plausibilityChecker.isDirectionFlipPhysicallyPlausible(
                        vehicleId, "OPPOSITE_FALLBACK", existing, oppositeSnap, oppositeSnap.fraction());

        if (oppositePlausible) {
            boolean hardMismatch =
                    primaryFailed.distanceMeters() > properties.getOppositeSnapHardPrimaryDistanceMeters()
                            && oppositeSnap.distanceMeters() < properties.getOppositeSnapHardOppositeDistanceMeters();
            int requiredSnaps = hardMismatch
                    ? properties.getOppositeSnapHardThreshold()
                    : properties.getOppositeSnapThreshold();

            log.debug("[GPS_PIPELINE] SNAP_OPPOSITE vehicle={} route={} dir={}→{} dist={}m (primary={}m) hardMismatch={} required={}",
                    vehicleId, routeNumber, currentDirection, oppositeDir,
                    String.format("%.1f", oppositeSnap.distanceMeters()),
                    String.format("%.1f", primaryFailed.distanceMeters()),
                    hardMismatch, requiredSnaps);

            int snapCount = oppositeCounter.incrementAndGet(vehicleId);
            if (snapCount >= requiredSnaps) {
                oppositeCounter.queueDirectionFix(vehicleId, oppositeDir);
                log.info("[GPS_PIPELINE] DIR_AUTO_FIX vehicle={} route={} dir={}→{} ({}x consecutive opposite snap, hardMismatch={})",
                        vehicleId, routeNumber, currentDirection, oppositeDir, snapCount, hardMismatch);
                oppositeCounter.reset(vehicleId);
            }

            return Result.accepted(oppositeDir, oppositeCoords, oppositeDist, oppositeSnap, rawSnapMinDistance);
        }

        if (oppositeSnap.snapped()) {
            double physicalJumpMeters = existing != null
                    && existing.getPredictedLatitude() != 0.0
                    && existing.getPredictedLongitude() != 0.0
                    ? DistanceCalculationService.haversineDistanceMeters(
                            existing.getPredictedLatitude(), existing.getPredictedLongitude(),
                            oppositeSnap.latitude(), oppositeSnap.longitude())
                    : -1;
            log.warn("[GPS_PIPELINE] OPPOSITE_FALLBACK_REJECTED vehicle={} plate={} route={} dir={}→{} " +
                            "primaryDist={}m oppositeDist={}m physicalJump={}m — GPS noise near parallel lanes, keeping predicted on route",
                    vehicleId, licensePlate, routeNumber, currentDirection, oppositeDir,
                    String.format("%.1f", primaryFailed.distanceMeters()),
                    String.format("%.1f", oppositeSnap.distanceMeters()),
                    physicalJumpMeters >= 0 ? String.format("%.0f", physicalJumpMeters) : "-");
        } else {
            log.debug("[GPS_PIPELINE] SNAP_FAIL vehicle={} route={} dist={}m (opposite={}m) > threshold={}m → keeping predicted on route, awaiting re-snap",
                    vehicleId, routeNumber,
                    String.format("%.1f", primaryFailed.distanceMeters()),
                    String.format("%.1f", oppositeSnap.distanceMeters()),
                    (int) properties.getMaxSnapDistanceMeters());
        }

        oppositeCounter.reset(vehicleId);
        return Result.notAccepted(currentDirection, rawSnapMinDistance);
    }
}
