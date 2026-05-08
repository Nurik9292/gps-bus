package biz.ugur.busroutebackend.transport.infrastructure.prediction.snap;

import biz.ugur.busroutebackend.transport.infrastructure.prediction.MapMatchingService;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.PredictionProperties;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.RouteGeometryCache;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePredictionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class HeadingFlipStrategy {

    private final PredictionProperties properties;
    private final RouteGeometryCache routeGeometryCache;
    private final MapMatchingService mapMatchingService;
    private final DirectionChangeCooldown cooldown;
    private final PlausibilityChecker plausibilityChecker;

    public HeadingFlipStrategy(PredictionProperties properties,
                                RouteGeometryCache routeGeometryCache,
                                MapMatchingService mapMatchingService,
                                DirectionChangeCooldown cooldown,
                                PlausibilityChecker plausibilityChecker) {
        this.properties = properties;
        this.routeGeometryCache = routeGeometryCache;
        this.mapMatchingService = mapMatchingService;
        this.cooldown = cooldown;
        this.plausibilityChecker = plausibilityChecker;
    }

    public record Result(
            boolean flipped,
            int direction,
            List<double[]> routeCoords,
            double totalDist,
            MapMatchingService.SnappedResult snap,
            double rawSnapMinDistance) {

        static Result notFlipped(int currentDirection,
                                  List<double[]> currentRouteCoords,
                                  double currentTotalDist,
                                  MapMatchingService.SnappedResult currentSnap,
                                  double rawSnapMinDistance) {
            return new Result(false, currentDirection, currentRouteCoords,
                    currentTotalDist, currentSnap, rawSnapMinDistance);
        }

        static Result flipped(int newDirection,
                               List<double[]> newRouteCoords,
                               double newTotalDist,
                               MapMatchingService.SnappedResult newSnap,
                               double rawSnapMinDistance) {
            return new Result(true, newDirection, newRouteCoords, newTotalDist, newSnap, rawSnapMinDistance);
        }
    }

    public Result maybeFlip(VehiclePredictionState existing,
                             String vehicleId, String licensePlate, String routeNumber,
                             double latitude, double longitude, double course,
                             int currentDirection, List<double[]> currentRouteCoords,
                             double currentTotalDist, double[] currentCumDist,
                             MapMatchingService.SnappedResult primarySnap,
                             double currentRawSnapMinDistance) {

        if (!primarySnap.snapped() || course <= 1.0) {
            return Result.notFlipped(currentDirection, currentRouteCoords, currentTotalDist,
                    primarySnap, currentRawSnapMinDistance);
        }

        double routeHeading = mapMatchingService.calculateCourseFromRoute(
                currentRouteCoords, currentCumDist, primarySnap.fraction(), currentDirection, currentTotalDist);
        double headingDiff = Math.abs(course - routeHeading);
        if (headingDiff > 180) {
            headingDiff = 360 - headingDiff;
        }

        if (headingDiff <= properties.getDirectionFlipThresholdDeg()) {
            return Result.notFlipped(currentDirection, currentRouteCoords, currentTotalDist,
                    primarySnap, currentRawSnapMinDistance);
        }

        boolean hardOppositeEvidence = headingDiff >= properties.getHeadingFlipHardOverrideDeg();

        if (cooldown.isActive(existing) && !hardOppositeEvidence) {
            log.info("[GPS_PIPELINE] DIR_FLIP_BLOCKED_COOLDOWN vehicle={} plate={} type=heading ageMs={} headingDiff={}° course={}° routeHeading={}°",
                    vehicleId, licensePlate, cooldown.ageMs(existing),
                    (int) headingDiff, (int) course, (int) routeHeading);
            return Result.notFlipped(currentDirection, currentRouteCoords, currentTotalDist,
                    primarySnap, currentRawSnapMinDistance);
        }

        if (cooldown.isActive(existing)) {
            log.info("[GPS_PIPELINE] DIR_FLIP_COOLDOWN_OVERRIDE vehicle={} plate={} type=heading ageMs={} headingDiff={}° threshold={}° course={}° routeHeading={}°",
                    vehicleId, licensePlate, cooldown.ageMs(existing),
                    (int) headingDiff, (int) properties.getHeadingFlipHardOverrideDeg(),
                    (int) course, (int) routeHeading);
        }

        int flippedDir = (currentDirection == 0) ? 1 : 0;
        List<double[]> flippedCoords = routeGeometryCache.getPoints(routeNumber, flippedDir);
        if (flippedCoords == null) {
            return Result.notFlipped(currentDirection, currentRouteCoords, currentTotalDist,
                    primarySnap, currentRawSnapMinDistance);
        }

        double flippedDist = routeGeometryCache.getTotalDistance(routeNumber, flippedDir);
        MapMatchingService.SnappedResult flippedSnap =
                mapMatchingService.snapToNearestSegment(latitude, longitude, flippedCoords, flippedDist);

        double rawSnapMinDistance = currentRawSnapMinDistance;
        if (flippedSnap.snapped()) {
            rawSnapMinDistance = Math.min(rawSnapMinDistance, flippedSnap.distanceMeters());
        }

        if (!flippedSnap.snapped()
                || !plausibilityChecker.isDirectionFlipPhysicallyPlausible(
                        vehicleId, "HEADING", existing, flippedSnap, primarySnap.fraction())) {
            return Result.notFlipped(currentDirection, currentRouteCoords, currentTotalDist,
                    primarySnap, rawSnapMinDistance);
        }

        log.debug("Direction corrected for vehicle {}: {} → {} (headingDiff={}°, course={}°, routeHeading={}°)",
                vehicleId, currentDirection, flippedDir,
                (int) headingDiff, (int) course, (int) routeHeading);

        return Result.flipped(flippedDir, flippedCoords, flippedDist, flippedSnap, rawSnapMinDistance);
    }
}
