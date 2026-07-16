package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.ConsecutiveOppositeCounter;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.DirectionChangeCooldown;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.FracFlipStrategy;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.HeadingFlipStrategy;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.ImplausibleJumpHandler;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.OppositeFallbackStrategy;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.PlausibilityChecker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
class SnapCorrector {


    private final PredictionProperties properties;
    private final RouteGeometryCache routeGeometryCache;
    private final MapMatchingService mapMatchingService;
    private final DirectionChangeCooldown cooldown;
    private final PlausibilityChecker plausibilityChecker;
    private final ConsecutiveOppositeCounter oppositeCounter;
    private final OppositeFallbackStrategy oppositeFallback;
    private final HeadingFlipStrategy headingFlip;
    private final FracFlipStrategy fracFlip;
    private final ImplausibleJumpHandler implausibleJumpHandler;
    private final PipelineTracer pipelineTracer;

    SnapCorrector(PredictionProperties properties,
                  RouteGeometryCache routeGeometryCache,
                  MapMatchingService mapMatchingService,
                  DirectionChangeCooldown cooldown,
                  PlausibilityChecker plausibilityChecker,
                  ConsecutiveOppositeCounter oppositeCounter,
                  OppositeFallbackStrategy oppositeFallback,
                  HeadingFlipStrategy headingFlip,
                  FracFlipStrategy fracFlip,
                  ImplausibleJumpHandler implausibleJumpHandler,
                  PipelineTracer pipelineTracer) {
        this.properties = properties;
        this.routeGeometryCache = routeGeometryCache;
        this.mapMatchingService = mapMatchingService;
        this.cooldown = cooldown;
        this.plausibilityChecker = plausibilityChecker;
        this.oppositeCounter = oppositeCounter;
        this.oppositeFallback = oppositeFallback;
        this.headingFlip = headingFlip;
        this.fracFlip = fracFlip;
        this.implausibleJumpHandler = implausibleJumpHandler;
        this.pipelineTracer = pipelineTracer;
    }

    private boolean anchorTooStale(VehiclePredictionState existing, Instant gpsTimestamp,
                                   String vehicleId, String licensePlate, String routeId) {
        Instant anchorAt = existing.getLastGpsUpdate();
        if (anchorAt == null || gpsTimestamp == null) {
            return false;
        }
        long ageMs = Duration.between(anchorAt, gpsTimestamp).toMillis();
        if (ageMs <= properties.getStaleAnchorMs()) {
            return false;
        }
        log.info("[GPS_PIPELINE] SNAP_ANCHOR_STALE vehicle={} plate={} route={} anchorAgeMs={} threshold={} — cold whole-line re-projection",
                vehicleId, licensePlate, routeId, ageMs, properties.getStaleAnchorMs());
        return true;
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
                         String vehicleId, String licensePlate, String routeId,
                         double latitude, double longitude, double course, int direction,
                         Instant gpsTimestamp) {

        final int inputDirection = direction;
        final double inputFraction = existing != null ? existing.getFractionOnRoute() : -1;

        double newRejectedFrac = existing != null ? existing.getLastRejectedGpsFraction() : -1;
        int newImplausibleCount = existing != null ? existing.getConsecutiveImplausibleCount() : 0;

        boolean routeReassigned = existing != null
                && existing.getRouteId() != null
                && routeId != null
                && !existing.getRouteId().equals(routeId);

        if (routeId == null || !properties.isSnapToRoute()) {
            log.debug("[GPS_PIPELINE] SNAP_SKIP vehicle={} snapToRoute={} routeId={}",
                    vehicleId, properties.isSnapToRoute(), routeId);
            pipelineTracer.traceSnap(vehicleId, licensePlate, routeId, direction,
                    Double.MAX_VALUE, -1, false, "SKIP_NO_ROUTE");
            return new SnapResult(latitude, longitude, -1, direction, null, 0,
                    course, newRejectedFrac, newImplausibleCount, false, Double.MAX_VALUE);
        }

        List<double[]> routeCoords = routeGeometryCache.getPoints(routeId, direction);
        if (routeCoords == null) {
            int opposite = (direction == 0) ? 1 : 0;
            routeCoords = routeGeometryCache.getPoints(routeId, opposite);
            if (routeCoords != null) {
                direction = opposite;
                pipelineTracer.traceSnap(vehicleId, licensePlate, routeId, direction,
                        Double.MAX_VALUE, -1, false, "GEOMETRY_FALLBACK_OPPOSITE");
            }
        }

        if (routeCoords == null) {
            log.debug("[GPS_PIPELINE] SNAP_SKIP vehicle={} snapToRoute={} routeId={}",
                    vehicleId, properties.isSnapToRoute(), routeId);
            pipelineTracer.traceSnap(vehicleId, licensePlate, routeId, direction,
                    Double.MAX_VALUE, -1, false, "SKIP_NO_GEOMETRY");
            return new SnapResult(latitude, longitude, -1, direction, null, 0,
                    course, newRejectedFrac, newImplausibleCount, false, Double.MAX_VALUE);
        }

        double totalDist = routeGeometryCache.getTotalDistance(routeId, direction);
        double[] cumDist = routeGeometryCache.getCumulativeDistances(routeId, direction);
        log.debug("[GPS_PIPELINE] SNAP_ATTEMPT vehicle={} plate={} route={} dir={} lat={} lon={} course={} polyline_first3=[{},{},{}] polyline_last3=[{},{},{}] polyline_size={}",
                vehicleId, licensePlate, routeId, direction, latitude, longitude, course,
                routeCoords.size() > 0 ? routeCoords.get(0)[0] + "," + routeCoords.get(0)[1] : "-",
                routeCoords.size() > 1 ? routeCoords.get(1)[0] + "," + routeCoords.get(1)[1] : "-",
                routeCoords.size() > 2 ? routeCoords.get(2)[0] + "," + routeCoords.get(2)[1] : "-",
                routeCoords.size() > 2 ? routeCoords.get(routeCoords.size()-3)[0] + "," + routeCoords.get(routeCoords.size()-3)[1] : "-",
                routeCoords.size() > 1 ? routeCoords.get(routeCoords.size()-2)[0] + "," + routeCoords.get(routeCoords.size()-2)[1] : "-",
                routeCoords.size() > 0 ? routeCoords.get(routeCoords.size()-1)[0] + "," + routeCoords.get(routeCoords.size()-1)[1] : "-",
                routeCoords.size());
        boolean anchorUsable = existing != null
                && existing.getLastGpsFraction() >= 0
                && !anchorTooStale(existing, gpsTimestamp, vehicleId, licensePlate, routeId);
        MapMatchingService.SnappedResult snap = anchorUsable
                ? mapMatchingService.snapToNearestSegment(latitude, longitude, routeCoords, cumDist, totalDist,
                        existing.getLastGpsFraction(), properties.getWindowedSnapFractionWindow())
                : mapMatchingService.snapToNearestSegment(latitude, longitude, routeCoords, totalDist);

        double rawSnapMinDistance = snap.distanceMeters();

        boolean fracCorrected = false;

        HeadingFlipStrategy.Result headingResult = headingFlip.maybeFlip(
                existing, vehicleId, licensePlate, routeId,
                latitude, longitude, course,
                direction, routeCoords, totalDist, cumDist,
                snap, rawSnapMinDistance);
        rawSnapMinDistance = headingResult.rawSnapMinDistance();
        boolean headingCorrected = headingResult.flipped();
        if (headingCorrected) {
            direction = headingResult.direction();
            routeCoords = headingResult.routeCoords();
            totalDist = headingResult.totalDist();
            snap = headingResult.snap();
        }

        double predictedLat;
        double predictedLon;
        double fraction;
        boolean resetTriggered = false;
        boolean oppositeFallbackAccepted = false;

        if (snap.snapped()) {
            log.debug("[GPS_PIPELINE] SNAP_OK vehicle={} route={} dist={}m frac={}",
                    vehicleId, routeId,
                    String.format("%.1f", snap.distanceMeters()),
                    String.format("%.4f", snap.fraction()));
            oppositeCounter.reset(vehicleId);
            double realFraction = snap.fraction();
            double predictedFraction = (existing != null) ? existing.getFractionOnRoute() : -1;

            FracFlipStrategy.Result fracResult = fracFlip.maybeFlip(
                    existing, vehicleId, licensePlate, routeId,
                    latitude, longitude,
                    direction, routeCoords, totalDist,
                    snap, rawSnapMinDistance, headingCorrected);
            rawSnapMinDistance = fracResult.rawSnapMinDistance();
            if (fracResult.flipped()) {
                direction = fracResult.direction();
                routeCoords = fracResult.routeCoords();
                totalDist = fracResult.totalDist();
                snap = fracResult.snap();
                realFraction = snap.fraction();
                fracCorrected = true;
            }

            boolean plausibleSnap = true;
            boolean resetToDR = false;

            boolean routeChanged = existing != null
                    && existing.getRouteId() != null
                    && !existing.getRouteId().equals(routeId);
            if (routeChanged) {
                newRejectedFrac = -1;
                newImplausibleCount = 0;
                log.debug("[GPS_PIPELINE] ROUTE_CHANGE vehicle={} route={}→{} frac={} — resetting snap state",
                        vehicleId, existing.getRouteId(), routeId,
                        String.format("%.4f", realFraction));
            }

            ImplausibleJumpHandler.Result implausible = implausibleJumpHandler.evaluate(
                    existing, vehicleId, routeId, direction, realFraction,
                    headingCorrected, fracCorrected, routeChanged,
                    newRejectedFrac, newImplausibleCount);
            plausibleSnap = implausible.plausibleSnap();
            resetToDR = implausible.resetToDR();
            newRejectedFrac = implausible.newRejectedFrac();
            newImplausibleCount = implausible.newImplausibleCount();
            if (implausible.resetTriggered()) {
                resetTriggered = true;
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
                            vehicleId, licensePlate, routeId, direction,
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
                        vehicleId, licensePlate, routeId,
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
            OppositeFallbackStrategy.Result fallback = oppositeFallback.tryFlip(
                    existing, vehicleId, licensePlate, routeId,
                    direction, latitude, longitude, snap, rawSnapMinDistance);
            rawSnapMinDistance = fallback.rawSnapMinDistance();

            if (fallback.accepted()) {
                direction = fallback.direction();
                routeCoords = fallback.routeCoords();
                totalDist = fallback.totalDist();
                snap = fallback.snap();
                double realFractionOpposite = snap.fraction();
                predictedLat = snap.latitude();
                predictedLon = snap.longitude();
                fraction = realFractionOpposite;
                double[] oppositeCumDist = routeGeometryCache.getCumulativeDistances(routeId, direction);
                course = mapMatchingService.calculateCourseFromRoute(routeCoords, oppositeCumDist, fraction, direction, totalDist);
                oppositeFallbackAccepted = true;
            } else {
                predictedLat = existing != null ? existing.getPredictedLatitude() : latitude;
                predictedLon = existing != null ? existing.getPredictedLongitude() : longitude;
                fraction = !routeReassigned && existing != null && existing.getFractionOnRoute() >= 0
                        ? existing.getFractionOnRoute()
                        : -1;
            }
        }

        String branch;
        if (oppositeFallbackAccepted) {
            branch = "OPPOSITE_FALLBACK";
        } else if (snap.snapped()) {
            if (fracCorrected) {
                branch = "FRAC_FLIP";
            } else if (headingCorrected) {
                branch = "HEADING_FLIP";
            } else if (resetTriggered) {
                branch = "PRIMARY_RESET";
            } else {
                branch = "PRIMARY";
            }
        } else {
            branch = "NO_SNAP";
        }
        pipelineTracer.traceSnap(vehicleId, licensePlate, routeId, direction,
                rawSnapMinDistance, fraction, snap.snapped(), branch);

        if (direction != inputDirection) {
            pipelineTracer.traceSnapStateDirectionMutation(
                    vehicleId, licensePlate, routeId,
                    inputDirection, direction, branch,
                    inputFraction, fraction, rawSnapMinDistance);
        }

        return new SnapResult(predictedLat, predictedLon, fraction, direction,
                routeCoords, totalDist, course, newRejectedFrac, newImplausibleCount,
                resetTriggered, rawSnapMinDistance);
    }

    Map<String, Integer> drainPendingDirectionFixes() {
        return oppositeCounter.drainPendingDirectionFixes();
    }

    void onVehicleStaleCleanup(java.util.Set<String> livingVehicleIds) {
        oppositeCounter.retainOnly(livingVehicleIds);
    }
}
