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
public class FracFlipStrategy {

    private static final double FRONTAGE_GUARD_PRIMARY_MAX_METERS = 30.0;
    private static final double FRONTAGE_GUARD_MIN_DELTA_METERS   = 30.0;

    private final PredictionProperties properties;
    private final RouteGeometryCache routeGeometryCache;
    private final MapMatchingService mapMatchingService;
    private final DirectionChangeCooldown cooldown;
    private final PlausibilityChecker plausibilityChecker;

    public FracFlipStrategy(PredictionProperties properties,
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
                             double latitude, double longitude,
                             int currentDirection, List<double[]> currentRouteCoords,
                             double currentTotalDist,
                             MapMatchingService.SnappedResult primarySnap,
                             double currentRawSnapMinDistance,
                             boolean headingCorrected) {

        Result noFlip = Result.notFlipped(currentDirection, currentRouteCoords,
                currentTotalDist, primarySnap, currentRawSnapMinDistance);

        if (headingCorrected || existing == null || existing.getLastGpsFraction() < 0) {
            return noFlip;
        }

        double realFraction = primarySnap.fraction();
        double lastGpsFrac = existing.getLastGpsFraction();

        if (cooldown.isActive(existing)) {
            log.info("[GPS_PIPELINE] DIR_FLIP_BLOCKED_COOLDOWN vehicle={} plate={} type=frac ageMs={} lastGpsFrac={} realFraction={}",
                    vehicleId, licensePlate, cooldown.ageMs(existing),
                    String.format("%.4f", lastGpsFrac),
                    String.format("%.4f", realFraction));
            return noFlip;
        }

        double fracDelta = realFraction - lastGpsFrac;
        boolean gpsMoveAgainstDir = fracDelta < -0.005;
        if (!gpsMoveAgainstDir) {
            return noFlip;
        }

        boolean plausibleJump = Math.abs(fracDelta) <= properties.getFracFlipPlausibleJumpThreshold();
        double tolerance = properties.getTerminalFractionTolerance();
        boolean wasNearTerminal = lastGpsFrac <= tolerance || lastGpsFrac >= (1.0 - tolerance);
        boolean nowNearOppositeTerminal = realFraction >= 0
                && (lastGpsFrac >= (1.0 - tolerance)
                        ? realFraction <= tolerance * 3
                        : realFraction >= (1.0 - tolerance * 3));
        boolean atTerminalFlip = wasNearTerminal && nowNearOppositeTerminal;
        boolean isStationary = !existing.isInMotion()
                || (existing.getRawGpsSpeedKmh() >= 0
                        && existing.getRawGpsSpeedKmh() < properties.getStationarySpeedThresholdKmh());

        if (isStationary && !atTerminalFlip) {
            log.debug("[GPS_PIPELINE] DIR_CORRECT_FRAC_SKIP_STATIONARY vehicle={} route={} dir={} delta={} inMotion={} rawSpeed={}km/h (GPS noise on stationary bus, not a real direction reversal)",
                    vehicleId, routeNumber, currentDirection,
                    String.format("%.4f", fracDelta),
                    existing.isInMotion(),
                    String.format("%.1f", existing.getRawGpsSpeedKmh()));
            return noFlip;
        }

        if (!plausibleJump && !atTerminalFlip) {
            log.debug("[GPS_PIPELINE] DIR_CORRECT_FRAC_SKIP vehicle={} route={} dir={} delta={} (jump too large or heading corrected)",
                    vehicleId, routeNumber, currentDirection, String.format("%.4f", fracDelta));
            return noFlip;
        }

        int correctedDir = (currentDirection == 0) ? 1 : 0;
        List<double[]> correctedCoords = routeGeometryCache.getPoints(routeNumber, correctedDir);
        if (correctedCoords == null) {
            return noFlip;
        }

        double correctedDist = routeGeometryCache.getTotalDistance(routeNumber, correctedDir);
        MapMatchingService.SnappedResult correctedSnap =
                mapMatchingService.snapToNearestSegment(latitude, longitude, correctedCoords, correctedDist);

        double rawSnapMinDistance = currentRawSnapMinDistance;
        if (correctedSnap.snapped()) {
            rawSnapMinDistance = Math.min(rawSnapMinDistance, correctedSnap.distanceMeters());
        }

        double primarySnapDist  = primarySnap.snapped()   ? primarySnap.distanceMeters()   : Double.POSITIVE_INFINITY;
        double oppositeSnapDist = correctedSnap.snapped() ? correctedSnap.distanceMeters() : Double.POSITIVE_INFINITY;
        boolean frontageRoadParallelRun = primarySnap.snapped()
                && primarySnapDist <= FRONTAGE_GUARD_PRIMARY_MAX_METERS
                && (primarySnapDist - oppositeSnapDist) < FRONTAGE_GUARD_MIN_DELTA_METERS;
        if (frontageRoadParallelRun && !atTerminalFlip) {
            log.info("[GPS_PIPELINE] DIR_FLIP_BLOCKED_FRONTAGE_GUARD vehicle={} route={} dir={} " +
                            "primaryDist={}m oppositeDist={}m delta={}m fracDelta={} — frontage-road parallel polylines, refusing flip",
                    vehicleId, routeNumber, currentDirection,
                    String.format("%.1f", primarySnapDist),
                    String.format("%.1f", oppositeSnapDist),
                    String.format("%.1f", primarySnapDist - oppositeSnapDist),
                    String.format("%.4f", fracDelta));
            return Result.notFlipped(currentDirection, currentRouteCoords,
                    currentTotalDist, primarySnap, rawSnapMinDistance);
        }

        boolean terminalFlipSmoothOnOpposite = wasNearTerminal && correctedSnap.snapped()
                && (lastGpsFrac >= (1.0 - tolerance)
                        ? correctedSnap.fraction() <= tolerance * 3
                        : correctedSnap.fraction() >= (1.0 - tolerance * 3));
        boolean flipAcceptable = correctedSnap.snapped()
                && (plausibleJump
                        ? plausibilityChecker.isDirectionFlipPhysicallyPlausible(
                                vehicleId, "FRAC", existing, correctedSnap, lastGpsFrac)
                        : terminalFlipSmoothOnOpposite);

        if (!flipAcceptable) {
            return Result.notFlipped(currentDirection, currentRouteCoords,
                    currentTotalDist, primarySnap, rawSnapMinDistance);
        }

        log.info("[GPS_PIPELINE] DIR_CORRECT_FRAC vehicle={} route={} dir={}→{} gpsFrac={}→{} oppositeFrac={} (delta={}{})",
                vehicleId, routeNumber, currentDirection, correctedDir,
                String.format("%.4f", lastGpsFrac),
                String.format("%.4f", realFraction),
                String.format("%.4f", correctedSnap.fraction()),
                String.format("%.4f", fracDelta),
                wasNearTerminal && !plausibleJump ? " terminal-flip" : "");

        return Result.flipped(correctedDir, correctedCoords, correctedDist, correctedSnap, rawSnapMinDistance);
    }
}
