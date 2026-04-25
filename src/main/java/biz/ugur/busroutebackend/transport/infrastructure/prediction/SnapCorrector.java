package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
class SnapCorrector {

    private static final long DIR_CHANGE_COOLDOWN_MS = 5_000;

    private final PredictionProperties properties;
    private final RouteGeometryCache routeGeometryCache;
    private final MapMatchingService mapMatchingService;

    private final ConcurrentHashMap<String, Integer> consecutiveOppositeSnaps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> pendingDirectionFixes = new ConcurrentHashMap<>();

    SnapCorrector(PredictionProperties properties,
                  RouteGeometryCache routeGeometryCache,
                  MapMatchingService mapMatchingService) {
        this.properties = properties;
        this.routeGeometryCache = routeGeometryCache;
        this.mapMatchingService = mapMatchingService;
    }

    record SnapResult(
            double predictedLatitude,
            double predictedLongitude,
            double fraction,
            int direction,
            List<double[]> routeCoords,
            double totalRouteDistanceMeters,
            double course,
            double newRejectedFrac,
            int newImplausibleCount,
            boolean resetTriggered,
            double rawSnapMinDistance
    ) {}

    SnapResult applySnap(VehiclePredictionState existing,
                         String vehicleId, String licensePlate, String routeNumber,
                         double latitude, double longitude, double course, int direction) {

        double newRejectedFrac = existing != null ? existing.getLastRejectedGpsFraction() : -1;
        int newImplausibleCount = existing != null ? existing.getConsecutiveImplausibleCount() : 0;

        boolean routeReassigned = existing != null
                && existing.getRouteNumber() != null
                && routeNumber != null
                && !existing.getRouteNumber().equals(routeNumber);

        if (routeNumber == null || !properties.isSnapToRoute()) {
            log.debug("[GPS_PIPELINE] SNAP_SKIP vehicle={} snapToRoute={} routeNumber={}",
                    vehicleId, properties.isSnapToRoute(), routeNumber);
            return new SnapResult(latitude, longitude, -1, direction, null, 0,
                    course, newRejectedFrac, newImplausibleCount, false, Double.MAX_VALUE);
        }

        List<double[]> routeCoords = routeGeometryCache.getPoints(routeNumber, direction);
        if (routeCoords == null) {
            int opposite = (direction == 0) ? 1 : 0;
            routeCoords = routeGeometryCache.getPoints(routeNumber, opposite);
            if (routeCoords != null) {
                direction = opposite;
            }
        }

        if (routeCoords == null) {
            log.debug("[GPS_PIPELINE] SNAP_SKIP vehicle={} snapToRoute={} routeNumber={}",
                    vehicleId, properties.isSnapToRoute(), routeNumber);
            return new SnapResult(latitude, longitude, -1, direction, null, 0,
                    course, newRejectedFrac, newImplausibleCount, false, Double.MAX_VALUE);
        }

        double totalDist = routeGeometryCache.getTotalDistance(routeNumber, direction);
        double[] cumDist = routeGeometryCache.getCumulativeDistances(routeNumber, direction);
        log.debug("[GPS_PIPELINE] SNAP_ATTEMPT vehicle={} route={} dir={} lat={} lon={}",
                vehicleId, routeNumber, direction, latitude, longitude);
        MapMatchingService.SnappedResult snap = (existing != null && existing.getLastGpsFraction() >= 0)
                ? mapMatchingService.snapToNearestSegment(latitude, longitude, routeCoords, cumDist, totalDist,
                        existing.getLastGpsFraction(), 0.20)
                : mapMatchingService.snapToNearestSegment(latitude, longitude, routeCoords, totalDist);

        double rawSnapMinDistance = snap.distanceMeters();

        boolean headingCorrected = false;
        boolean fracCorrected = false;

        if (snap.snapped() && course > 1.0) {
            double routeHeading = mapMatchingService.calculateCourseFromRoute(
                    routeCoords, cumDist, snap.fraction(), direction, totalDist);
            double headingDiff = Math.abs(course - routeHeading);
            if (headingDiff > 180) headingDiff = 360 - headingDiff;
            if (headingDiff > properties.getDirectionFlipThresholdDeg()) {
                if (isInDirectionCooldown(existing)) {
                    log.info("[GPS_PIPELINE] DIR_FLIP_BLOCKED_COOLDOWN vehicle={} plate={} type=heading ageMs={} headingDiff={}° course={}° routeHeading={}°",
                            vehicleId, licensePlate, directionCooldownAgeMs(existing),
                            (int) headingDiff, (int) course, (int) routeHeading);
                } else {
                    int flippedDir = (direction == 0) ? 1 : 0;
                    List<double[]> flippedCoords = routeGeometryCache.getPoints(routeNumber, flippedDir);
                    if (flippedCoords != null) {
                        double flippedDist = routeGeometryCache.getTotalDistance(routeNumber, flippedDir);
                        MapMatchingService.SnappedResult flippedSnap =
                                mapMatchingService.snapToNearestSegment(latitude, longitude, flippedCoords, flippedDist);
                        if (flippedSnap.snapped()) {
                            rawSnapMinDistance = Math.min(rawSnapMinDistance, flippedSnap.distanceMeters());
                        }
                        if (flippedSnap.snapped()
                                && isDirectionFlipPhysicallyPlausible(vehicleId, "HEADING", existing,
                                        flippedSnap, snap.fraction())) {
                            log.debug("Direction corrected for vehicle {}: {} → {} (headingDiff={}°, course={}°, routeHeading={}°)",
                                    vehicleId, direction, flippedDir,
                                    (int) headingDiff, (int) course, (int) routeHeading);
                            direction = flippedDir;
                            routeCoords = flippedCoords;
                            totalDist = flippedDist;
                            snap = flippedSnap;
                            headingCorrected = true;
                        }
                    }
                }
            }
        }

        double predictedLat;
        double predictedLon;
        double fraction;
        boolean resetTriggered = false;

        if (snap.snapped()) {
            log.debug("[GPS_PIPELINE] SNAP_OK vehicle={} route={} dist={}m frac={}",
                    vehicleId, routeNumber,
                    String.format("%.1f", snap.distanceMeters()),
                    String.format("%.4f", snap.fraction()));
            consecutiveOppositeSnaps.remove(vehicleId);
            double realFraction = snap.fraction();
            double predictedFraction = (existing != null) ? existing.getFractionOnRoute() : -1;

            if (!headingCorrected && existing != null && existing.getLastGpsFraction() >= 0
                    && isInDirectionCooldown(existing)) {
                log.info("[GPS_PIPELINE] DIR_FLIP_BLOCKED_COOLDOWN vehicle={} plate={} type=frac ageMs={} lastGpsFrac={} realFraction={}",
                        vehicleId, licensePlate, directionCooldownAgeMs(existing),
                        String.format("%.4f", existing.getLastGpsFraction()),
                        String.format("%.4f", realFraction));
            } else if (!headingCorrected && existing != null && existing.getLastGpsFraction() >= 0) {
                double lastGpsFrac = existing.getLastGpsFraction();
                double fracDelta = realFraction - lastGpsFrac;
                boolean gpsMoveAgainstDir = fracDelta < -0.005;
                boolean plausibleJump = Math.abs(fracDelta) <= 0.25;
                double tolerance = properties.getTerminalFractionTolerance();
                boolean wasNearTerminal = lastGpsFrac <= tolerance || lastGpsFrac >= (1.0 - tolerance);
                boolean nowNearOppositeTerminal = realFraction >= 0
                        && (lastGpsFrac >= (1.0 - tolerance) ? realFraction <= tolerance * 3
                                : realFraction >= (1.0 - tolerance * 3));
                boolean atTerminalFlip = wasNearTerminal && nowNearOppositeTerminal;
                boolean isStationary = existing.getKalmanSpeedKmh() >= 0
                        && existing.getKalmanSpeedKmh() < 5.0;

                if (gpsMoveAgainstDir && isStationary && !atTerminalFlip) {
                    log.debug("[GPS_PIPELINE] DIR_CORRECT_FRAC_SKIP_STATIONARY vehicle={} route={} dir={} delta={} kalmanSpeed={}km/h (GPS noise on stationary bus, not a real direction reversal)",
                            vehicleId, routeNumber, direction,
                            String.format("%.4f", fracDelta),
                            String.format("%.1f", existing.getKalmanSpeedKmh()));
                } else if (gpsMoveAgainstDir && (plausibleJump || atTerminalFlip)) {
                    int correctedDir = (direction == 0) ? 1 : 0;
                    List<double[]> correctedCoords = routeGeometryCache.getPoints(routeNumber, correctedDir);
                    if (correctedCoords != null) {
                        double correctedDist = routeGeometryCache.getTotalDistance(routeNumber, correctedDir);
                        MapMatchingService.SnappedResult correctedSnap =
                                mapMatchingService.snapToNearestSegment(latitude, longitude, correctedCoords, correctedDist);
                        if (correctedSnap.snapped()) {
                            rawSnapMinDistance = Math.min(rawSnapMinDistance, correctedSnap.distanceMeters());
                        }
                        boolean terminalFlipSmoothOnOpposite = wasNearTerminal && correctedSnap.snapped()
                                && (lastGpsFrac >= (1.0 - tolerance)
                                        ? correctedSnap.fraction() <= tolerance * 3
                                        : correctedSnap.fraction() >= (1.0 - tolerance * 3));
                        boolean flipAcceptable = correctedSnap.snapped()
                                && (plausibleJump
                                        ? isDirectionFlipPhysicallyPlausible(vehicleId, "FRAC", existing,
                                                correctedSnap, lastGpsFrac)
                                        : terminalFlipSmoothOnOpposite);
                        if (flipAcceptable) {
                            log.info("[GPS_PIPELINE] DIR_CORRECT_FRAC vehicle={} route={} dir={}→{} gpsFrac={}→{} oppositeFrac={} (delta={}{})",
                                    vehicleId, routeNumber, direction, correctedDir,
                                    String.format("%.4f", lastGpsFrac),
                                    String.format("%.4f", realFraction),
                                    String.format("%.4f", correctedSnap.fraction()),
                                    String.format("%.4f", fracDelta),
                                    wasNearTerminal && !plausibleJump ? " terminal-flip" : "");
                            direction = correctedDir;
                            routeCoords = correctedCoords;
                            totalDist = correctedDist;
                            snap = correctedSnap;
                            realFraction = snap.fraction();
                            fracCorrected = true;
                        }
                    }
                } else if (gpsMoveAgainstDir) {
                    log.debug("[GPS_PIPELINE] DIR_CORRECT_FRAC_SKIP vehicle={} route={} dir={} delta={} (jump too large or heading corrected)",
                            vehicleId, routeNumber, direction, String.format("%.4f", fracDelta));
                }
            }

            boolean plausibleSnap = true;
            boolean resetToDR = false;

            boolean routeChanged = existing != null
                    && existing.getRouteNumber() != null
                    && !existing.getRouteNumber().equals(routeNumber);
            if (routeChanged) {
                newRejectedFrac = -1;
                newImplausibleCount = 0;
                log.debug("[GPS_PIPELINE] ROUTE_CHANGE vehicle={} route={}→{} frac={} — resetting snap state",
                        vehicleId, existing.getRouteNumber(), routeNumber,
                        String.format("%.4f", realFraction));
            }

            if (!headingCorrected && !fracCorrected && !routeChanged && existing != null && existing.getLastGpsFraction() >= 0) {
                double jumpSize = Math.abs(realFraction - existing.getLastGpsFraction());
                if (jumpSize > 0.25) {
                    boolean sameRejectedLocation = existing.getLastRejectedGpsFraction() >= 0
                            && Math.abs(realFraction - existing.getLastRejectedGpsFraction()) < 0.05;
                    if (sameRejectedLocation) {
                        newImplausibleCount = existing.getConsecutiveImplausibleCount() + 1;
                    } else {
                        newImplausibleCount = 1;
                    }
                    newRejectedFrac = realFraction;

                    if (newImplausibleCount >= 3) {
                        log.info("[GPS_PIPELINE] SNAP_IMPLAUSIBLE_RESET vehicle={} route={} dir={} frac={}→{} jump={} ({}x) — resetting to dead-reckoning at GPS position",
                                vehicleId, routeNumber, direction,
                                String.format("%.4f", existing.getLastGpsFraction()),
                                String.format("%.4f", realFraction),
                                String.format("%.4f", jumpSize),
                                newImplausibleCount);
                        newImplausibleCount = 0;
                        newRejectedFrac = -1;
                        resetToDR = true;
                        resetTriggered = true;
                    } else {
                        plausibleSnap = false;
                        resetTriggered = true;
                        log.debug("[GPS_PIPELINE] SNAP_IMPLAUSIBLE vehicle={} route={} dir={} lastFrac={}→newFrac={} jump={} ({}/3) — keeping predicted, entering cold-start",
                                vehicleId, routeNumber, direction,
                                String.format("%.4f", existing.getLastGpsFraction()),
                                String.format("%.4f", realFraction),
                                String.format("%.4f", jumpSize),
                                newImplausibleCount);
                    }
                } else {
                    newRejectedFrac = -1;
                    newImplausibleCount = 0;
                }
            }

            boolean realIsAhead = headingCorrected || fracCorrected
                    || (plausibleSnap && (predictedFraction < 0 || realFraction >= predictedFraction));

            if (headingCorrected || fracCorrected) {
                boolean noOpFlip = existing != null
                        && Math.abs(predictedFraction - realFraction) < 0.01
                        && DistanceCalculationService.haversineDistanceMeters(
                                existing.getPredictedLatitude(), existing.getPredictedLongitude(),
                                snap.latitude(), snap.longitude()) < 50.0;
                if (!noOpFlip) {
                    log.warn("[GPS_PIPELINE] DIR_FLIP_ACCEPT vehicle={} plate={} route={} dir={} " +
                                    "oldFrac={}→newFrac={} oldPos=({},{})→newPos=({},{}) realIsAhead={}",
                            vehicleId, licensePlate, routeNumber, direction,
                            String.format("%.4f", predictedFraction),
                            String.format("%.4f", realFraction),
                            existing != null ? String.format("%.5f", existing.getPredictedLatitude()) : "-",
                            existing != null ? String.format("%.5f", existing.getPredictedLongitude()) : "-",
                            String.format("%.5f", snap.latitude()),
                            String.format("%.5f", snap.longitude()),
                            realIsAhead);
                }
            }

            double snapVsGpsDistance = DistanceCalculationService.haversineDistanceMeters(
                    snap.latitude(), snap.longitude(), latitude, longitude);
            if (snapVsGpsDistance > properties.getTeleportThresholdMeters()) {
                log.warn("[GPS_PIPELINE] SNAP_TOO_FAR_FROM_GPS vehicle={} plate={} route={} " +
                                "snapDist={}m gps=({},{}) snap=({},{}) frac={} — keeping predicted on route, awaiting re-snap",
                        vehicleId, licensePlate, routeNumber,
                        String.format("%.0f", snapVsGpsDistance),
                        String.format("%.5f", latitude), String.format("%.5f", longitude),
                        String.format("%.5f", snap.latitude()), String.format("%.5f", snap.longitude()),
                        String.format("%.4f", realFraction));
                predictedLat = existing != null ? existing.getPredictedLatitude() : latitude;
                predictedLon = existing != null ? existing.getPredictedLongitude() : longitude;
                fraction = !routeReassigned && existing != null && existing.getFractionOnRoute() >= 0
                        ? existing.getFractionOnRoute()
                        : -1;
                resetTriggered = true;
            } else if (realIsAhead) {
                predictedLat = snap.latitude();
                predictedLon = snap.longitude();
                fraction = realFraction;
                course = mapMatchingService.calculateCourseFromRoute(routeCoords, cumDist, fraction, direction, totalDist);
                newRejectedFrac = -1;
                newImplausibleCount = 0;
            } else {
                predictedLat = existing.getPredictedLatitude();
                predictedLon = existing.getPredictedLongitude();
                fraction = existing.getFractionOnRoute();
                course = mapMatchingService.calculateCourseFromRoute(routeCoords, cumDist, fraction, direction, totalDist);
                log.trace("GPS behind predicted for vehicle {} (real={}, predicted={}); keeping predicted",
                        vehicleId, realFraction, predictedFraction);
            }

            if (resetToDR) {
                predictedLat = existing != null ? existing.getPredictedLatitude() : latitude;
                predictedLon = existing != null ? existing.getPredictedLongitude() : longitude;
                fraction = -1;
            }
        } else {
            int oppositeDir = (direction == 0) ? 1 : 0;
            List<double[]> oppositeCoords = routeGeometryCache.getPoints(routeNumber, oppositeDir);
            if (oppositeCoords != null) {
                double oppositeDist = routeGeometryCache.getTotalDistance(routeNumber, oppositeDir);
                MapMatchingService.SnappedResult oppositeSnap =
                        mapMatchingService.snapToNearestSegment(latitude, longitude, oppositeCoords, oppositeDist);
                if (oppositeSnap.snapped()) {
                    rawSnapMinDistance = Math.min(rawSnapMinDistance, oppositeSnap.distanceMeters());
                }
                boolean oppositePlausible = oppositeSnap.snapped()
                        && isDirectionFlipPhysicallyPlausible(vehicleId, "OPPOSITE_FALLBACK",
                                existing, oppositeSnap, oppositeSnap.fraction());
                if (oppositePlausible) {
                    log.debug("[GPS_PIPELINE] SNAP_OPPOSITE vehicle={} route={} dir={}→{} dist={}m (primary={}m)",
                            vehicleId, routeNumber, direction, oppositeDir,
                            String.format("%.1f", oppositeSnap.distanceMeters()),
                            String.format("%.1f", snap.distanceMeters()));
                    int snapCount = consecutiveOppositeSnaps.merge(vehicleId, 1, Integer::sum);
                    if (snapCount >= properties.getOppositeSnapThreshold()) {
                        pendingDirectionFixes.put(vehicleId, oppositeDir);
                        log.info("[GPS_PIPELINE] DIR_AUTO_FIX vehicle={} route={} dir={}→{} ({}x consecutive opposite snap)",
                                vehicleId, routeNumber, direction, oppositeDir, snapCount);
                        consecutiveOppositeSnaps.remove(vehicleId);
                    }
                    direction = oppositeDir;
                    routeCoords = oppositeCoords;
                    totalDist = oppositeDist;
                    snap = oppositeSnap;
                    double realFractionOpposite = snap.fraction();
                    predictedLat = snap.latitude();
                    predictedLon = snap.longitude();
                    fraction = realFractionOpposite;
                    double[] oppositeCumDist = routeGeometryCache.getCumulativeDistances(routeNumber, direction);
                    course = mapMatchingService.calculateCourseFromRoute(routeCoords, oppositeCumDist, fraction, direction, totalDist);
                } else {
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
                                vehicleId, licensePlate, routeNumber, direction, oppositeDir,
                                String.format("%.1f", snap.distanceMeters()),
                                String.format("%.1f", oppositeSnap.distanceMeters()),
                                physicalJumpMeters >= 0 ? String.format("%.0f", physicalJumpMeters) : "-");
                    } else {
                        log.debug("[GPS_PIPELINE] SNAP_FAIL vehicle={} route={} dist={}m (opposite={}m) > threshold={}m → keeping predicted on route, awaiting re-snap",
                                vehicleId, routeNumber,
                                String.format("%.1f", snap.distanceMeters()),
                                String.format("%.1f", oppositeSnap.distanceMeters()),
                                (int) MapMatchingService.MAX_SNAP_DISTANCE_METERS);
                    }
                    consecutiveOppositeSnaps.remove(vehicleId);
                    predictedLat = existing != null ? existing.getPredictedLatitude() : latitude;
                    predictedLon = existing != null ? existing.getPredictedLongitude() : longitude;
                    fraction = !routeReassigned && existing != null && existing.getFractionOnRoute() >= 0
                            ? existing.getFractionOnRoute()
                            : -1;
                }
            } else {
                log.debug("[GPS_PIPELINE] SNAP_FAIL vehicle={} route={} dist={}m > threshold={}m → keeping predicted on route, awaiting re-snap",
                        vehicleId, routeNumber,
                        String.format("%.1f", snap.distanceMeters()),
                        (int) MapMatchingService.MAX_SNAP_DISTANCE_METERS);
                consecutiveOppositeSnaps.remove(vehicleId);
                predictedLat = existing != null ? existing.getPredictedLatitude() : latitude;
                predictedLon = existing != null ? existing.getPredictedLongitude() : longitude;
                fraction = existing != null && existing.getFractionOnRoute() >= 0
                        ? existing.getFractionOnRoute()
                        : -1;
            }
        }

        return new SnapResult(predictedLat, predictedLon, fraction, direction,
                routeCoords, totalDist, course, newRejectedFrac, newImplausibleCount,
                resetTriggered, rawSnapMinDistance);
    }

    Map<String, Integer> drainPendingDirectionFixes() {
        if (pendingDirectionFixes.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> result = new HashMap<>(pendingDirectionFixes);
        pendingDirectionFixes.clear();
        return result;
    }

    void onVehicleStaleCleanup(java.util.Set<String> livingVehicleIds) {
        consecutiveOppositeSnaps.keySet().retainAll(livingVehicleIds);
    }

    private static boolean isInDirectionCooldown(VehiclePredictionState existing) {
        if (existing == null || existing.getDirectionChangedAt() == null) {
            return false;
        }
        long ageMs = Duration.between(existing.getDirectionChangedAt(), Instant.now()).toMillis();
        return ageMs >= 0 && ageMs < DIR_CHANGE_COOLDOWN_MS;
    }

    private static long directionCooldownAgeMs(VehiclePredictionState existing) {
        if (existing == null || existing.getDirectionChangedAt() == null) {
            return -1;
        }
        return Duration.between(existing.getDirectionChangedAt(), Instant.now()).toMillis();
    }

    private boolean isDirectionFlipPhysicallyPlausible(String vehicleId, String trigger,
                                                        VehiclePredictionState existing,
                                                        MapMatchingService.SnappedResult flippedSnap,
                                                        double curFraction) {
        if (existing == null || existing.getPredictedLatitude() == 0.0 || existing.getPredictedLongitude() == 0.0) {
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
