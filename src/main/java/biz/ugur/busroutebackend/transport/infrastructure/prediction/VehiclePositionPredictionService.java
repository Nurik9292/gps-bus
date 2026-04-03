package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.DirectVehiclePositionBroadcaster;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


@Service
@Slf4j
public class VehiclePositionPredictionService {

    private static final double METRES_PER_DEGREE_LAT = 111_320.0;
    private static final double DT_SECONDS = 1.0;

    /** GPS within this distance from prediction → smooth blend; farther → smooth correction. */
    private static final double MAX_CORRECTION_DISTANCE_METERS = 50.0;
    /** Max bus speed for outlier detection: 25 m/s = 90 km/h. */
    private static final double MAX_BUS_SPEED_MS = 25.0;
    /** Tolerance multiplier for outlier check.
     *  1.0 = strict (reject GPS implying >90 km/h — city bus physical limit).
     *  GPS batch updates every ~7s: 200m in 7s = 103 km/h → rejected, prediction continues smoothly. */
    private static final double OUTLIER_TOLERANCE = 1.0;
    /** If GPS heading differs from route heading by more than this → flip direction. */
    private static final double DIRECTION_FLIP_THRESHOLD_DEG = 90.0;
    /** Number of prediction cycles over which a large GPS jump is smoothed. */
    private static final int SMOOTH_CORRECTION_CYCLES = 5;
    /** GPS older than this is rejected by prediction engine (same as WebSocket stale filter). */
    private static final long MAX_GPS_AGE_MS = 10 * 60 * 1000L; // 10 minutes
    /** If snapped GPS is this far from predicted → override realIsAhead (bus turned around). */
    private static final double DIRECTION_CHANGE_DISTANCE_METERS = 150.0;

    private final ConcurrentHashMap<String, VehiclePredictionState> vehicleStates = new ConcurrentHashMap<>();

    private final PredictionProperties properties;
    private final DirectVehiclePositionBroadcaster directBroadcaster;
    private final RouteGeometryCache routeGeometryCache;
    private final MapMatchingService mapMatchingService;

    public VehiclePositionPredictionService(PredictionProperties properties,
                                             DirectVehiclePositionBroadcaster directBroadcaster,
                                             RouteGeometryCache routeGeometryCache,
                                             MapMatchingService mapMatchingService) {
        this.properties = properties;
        this.directBroadcaster = directBroadcaster;
        this.routeGeometryCache = routeGeometryCache;
        this.mapMatchingService = mapMatchingService;
    }


    public void onGpsUpdate(String vehicleId,
                            String licensePlate,
                            String routeNumber,
                            double latitude,
                            double longitude,
                            double speedKmh,
                            double course,
                            boolean inMotion,
                            Instant timestamp,
                            int direction) {
        if (!properties.isEnabled()) {
            return;
        }

        // Fix #9: Reject stale GPS before it pollutes the prediction state map.
        // Ancient positions (months/years old) cause incorrect outlier tolerance and
        // interfere with direction detection when fresh GPS eventually arrives.
        long gpsAgeMs = Instant.now().toEpochMilli() - timestamp.toEpochMilli();
        if (gpsAgeMs > MAX_GPS_AGE_MS) {
            log.trace("Stale GPS rejected in prediction engine for vehicle {}: age={}min",
                    vehicleId, gpsAgeMs / 60_000);
            return;
        }

        VehiclePredictionState existing = vehicleStates.get(vehicleId);

        // Deduplication: ignore if not newer than last known GPS
        if (existing != null && !timestamp.isAfter(existing.getLastGpsUpdate())) {
            log.trace("Ignoring duplicate GPS for vehicle {}: timestamp {} <= lastGpsUpdate {}",
                    vehicleId, timestamp, existing.getLastGpsUpdate());
            return;
        }

        // Fix 4: Outlier detection — reject physically impossible GPS jumps
        if (existing != null) {
            long elapsedMs = timestamp.toEpochMilli() - existing.getLastGpsUpdate().toEpochMilli();
            if (elapsedMs > 0 && elapsedMs < 300_000) {
                double distFromLastGps = DistanceCalculationService.haversineDistanceMeters(
                        existing.getGpsLatitude(), existing.getGpsLongitude(), latitude, longitude);
                double maxPossibleDist = (elapsedMs / 1000.0) * MAX_BUS_SPEED_MS * OUTLIER_TOLERANCE;
                if (distFromLastGps > maxPossibleDist) {
                    log.warn("GPS outlier rejected for vehicle {}: {}m in {}ms (max {}m at {}km/h×{})",
                            vehicleId, (int) distFromLastGps, elapsedMs,
                            (int) maxPossibleDist, (int) (MAX_BUS_SPEED_MS * 3.6), OUTLIER_TOLERANCE);
                    return;
                }
            }
        }

        double predictedLat;
        double predictedLon;
        double fraction;
        boolean needsSmoothCorrection = false;
        double smoothTargetLat = latitude;
        double smoothTargetLon = longitude;

        List<double[]> routeCoords = null;
        double totalDist = 0;

        if (routeNumber != null && properties.isSnapToRoute()) {
            routeCoords = routeGeometryCache.getPoints(routeNumber, direction);
            if (routeCoords == null) {
                int opposite = (direction == 0) ? 1 : 0;
                routeCoords = routeGeometryCache.getPoints(routeNumber, opposite);
                if (routeCoords != null) {
                    direction = opposite;
                }
            }
        }

        if (routeCoords != null) {
            totalDist = routeGeometryCache.getTotalDistance(routeNumber, direction);
            MapMatchingService.SnappedResult snap =
                    mapMatchingService.snapToNearestSegment(latitude, longitude, routeCoords, totalDist);

            // Fix 1: Validate direction via GPS heading — flip if route heading contradicts course
            if (snap.snapped() && course > 1.0) {
                double routeHeading = mapMatchingService.calculateCourseFromRoute(
                        routeCoords, snap.fraction(), direction, totalDist);
                double headingDiff = Math.abs(course - routeHeading);
                if (headingDiff > 180) headingDiff = 360 - headingDiff;
                if (headingDiff > DIRECTION_FLIP_THRESHOLD_DEG) {
                    int flippedDir = (direction == 0) ? 1 : 0;
                    List<double[]> flippedCoords = routeGeometryCache.getPoints(routeNumber, flippedDir);
                    if (flippedCoords != null) {
                        double flippedDist = routeGeometryCache.getTotalDistance(routeNumber, flippedDir);
                        MapMatchingService.SnappedResult flippedSnap =
                                mapMatchingService.snapToNearestSegment(latitude, longitude, flippedCoords, flippedDist);
                        if (flippedSnap.snapped()) {
                            log.debug("Direction corrected for vehicle {}: {} → {} (headingDiff={}°, course={}°, routeHeading={}°)",
                                    vehicleId, direction, flippedDir,
                                    (int) headingDiff, (int) course, (int) routeHeading);
                            direction = flippedDir;
                            routeCoords = flippedCoords;
                            totalDist = flippedDist;
                            snap = flippedSnap;
                        }
                    }
                }
            }

            if (snap.snapped()) {
                double realFraction = snap.fraction();
                double predictedFraction = (existing != null) ? existing.getFractionOnRoute() : -1;

                boolean realIsAhead = (predictedFraction < 0)
                        || (direction == 0 && realFraction >= predictedFraction)
                        || (direction == 1 && realFraction <= predictedFraction);

                // Fix #2: If snapped position is far from predicted, bus likely turned around
                // at terminal. Override realIsAhead to prevent freezing on boundary.
                if (!realIsAhead && existing != null) {
                    double distFromPredicted = DistanceCalculationService.haversineDistanceMeters(
                            existing.getPredictedLatitude(), existing.getPredictedLongitude(),
                            snap.latitude(), snap.longitude());
                    if (distFromPredicted > DIRECTION_CHANGE_DISTANCE_METERS) {
                        log.debug("Direction change override for vehicle {}: {}m gap, accepting GPS (fraction {} → {})",
                                vehicleId, (int) distFromPredicted, predictedFraction, realFraction);
                        realIsAhead = true;
                    }
                }

                if (realIsAhead) {
                    // Fix #3: If snap position is far from current predicted (>MAX_CORRECTION),
                    // use smooth correction instead of instant jump to avoid visual teleportation.
                    if (existing != null) {
                        double snapDist = DistanceCalculationService.haversineDistanceMeters(
                                existing.getPredictedLatitude(), existing.getPredictedLongitude(),
                                snap.latitude(), snap.longitude());
                        if (snapDist > MAX_CORRECTION_DISTANCE_METERS) {
                            needsSmoothCorrection = true;
                            smoothTargetLat = snap.latitude();
                            smoothTargetLon = snap.longitude();
                            log.debug("Snap correction started for vehicle {}: {}m to route snap",
                                    vehicleId, (int) snapDist);
                        }
                    }
                    predictedLat = needsSmoothCorrection && existing != null
                            ? existing.getPredictedLatitude() : snap.latitude();
                    predictedLon = needsSmoothCorrection && existing != null
                            ? existing.getPredictedLongitude() : snap.longitude();
                    // When smooth correction is active, keep the old fraction so the route
                    // interpolation stays in sync with the position being smoothed.
                    // The fraction advances naturally each prediction tick, converging toward
                    // the real GPS fraction as the position blends toward the snap point.
                    fraction = needsSmoothCorrection && existing != null && existing.getFractionOnRoute() >= 0
                            ? existing.getFractionOnRoute()
                            : realFraction;
                    course = mapMatchingService.calculateCourseFromRoute(routeCoords, fraction, direction, totalDist);
                } else {
                    predictedLat = existing.getPredictedLatitude();
                    predictedLon = existing.getPredictedLongitude();
                    fraction = existing.getFractionOnRoute();
                    course = mapMatchingService.calculateCourseFromRoute(routeCoords, fraction, direction, totalDist);
                    log.trace("GPS behind predicted for vehicle {} (real={}, predicted={}); keeping predicted",
                            vehicleId, realFraction, predictedFraction);
                }
            } else {
                // Not snapped to route — fall back to blended GPS position
                predictedLat = blendOrAccept(existing, latitude, longitude, true);
                predictedLon = blendOrAccept(existing, latitude, longitude, false);
                fraction = -1;
                routeCoords = null;
                totalDist = 0;
            }

        } else {
            // Dead-reckoning mode (no route geometry available).
            // Always snap to real GPS position — outlier detection above already ensures
            // the GPS is physically valid (< MAX_BUS_SPEED_MS × elapsed). Smooth correction
            // is avoided here because it compounds with dead-reckoning each cycle and can
            // diverge when GPS updates race with the prediction scheduler.
            predictedLat = latitude;
            predictedLon = longitude;
            fraction = -1;
        }

        VehiclePredictionState.VehiclePredictionStateBuilder builder = VehiclePredictionState.builder()
                .vehicleId(vehicleId)
                .licensePlate(licensePlate)
                .routeNumber(routeNumber)
                .gpsLatitude(latitude)
                .gpsLongitude(longitude)
                .speedKmh(speedKmh)
                .course(course)
                .inMotion(inMotion)
                .lastGpsUpdate(timestamp)
                .predictedLatitude(predictedLat)
                .predictedLongitude(predictedLon)
                .routeCoordinates(routeCoords)
                .totalRouteDistanceMeters(totalDist)
                .fractionOnRoute(fraction)
                .direction(direction);

        if (needsSmoothCorrection) {
            // smoothTargetLat/Lon is set to snap position (route-aware) or real GPS (dead-reckoning)
            builder.correctionTargetLat(smoothTargetLat)
                   .correctionTargetLon(smoothTargetLon)
                   .correctionCyclesLeft(SMOOTH_CORRECTION_CYCLES);
        }

        vehicleStates.put(vehicleId, builder.lastReceivedAt(Instant.now()).build());
        log.trace("GPS stored: vehicleId={}, speed={}km/h, inMotion={}, fraction={}",
                vehicleId, speedKmh, inMotion, fraction);
    }

    public Mono<Void> predictNextPositions() {
        if (!properties.isEnabled()) {
            return Mono.empty();
        }

        cleanupStaleStates();

        Instant now = Instant.now();
        long maxAgeMs = properties.getMaxAgeMs();
        double minSpeed = properties.getMinSpeedKmh();

        // Fix #1: When bus reaches terminal, reset fractionOnRoute so the next real GPS
        // update is accepted freely (predictedFraction < 0 → realIsAhead = true always).
        // Without this, the bus freezes at the terminal until direction flips in DB,
        // then jumps to wherever the bus actually is → teleportation.
        vehicleStates.values().forEach(state -> {
            if (isAtRouteBoundary(state)) {
                vehicleStates.put(state.getVehicleId(),
                        state.toBuilder().fractionOnRoute(-1).build());
            }
        });

        List<VehiclePredictionState> activeStates = vehicleStates.values().stream()
                .filter(state -> state.isInMotion() && state.getSpeedKmh() >= minSpeed)
                // Use server receipt time, not GPS fix time. Fix timestamps can lag server
                // by 5–30 s (batching + network); comparing fix time to maxAgeMs=10 s would
                // exclude every vehicle. Receipt time is always within seconds of "now".
                .filter(state -> state.getLastReceivedAt() != null
                        && (now.toEpochMilli() - state.getLastReceivedAt().toEpochMilli()) <= maxAgeMs)
                // For route-aware states: skip if at boundary (fractionOnRoute was reset to -1).
                // For dead-reckoning states (routeCoordinates=null): always advance —
                // these vehicles have no route assigned, so fractionOnRoute stays -1 forever
                // and without this exception they'd never get predicted movement between GPS batches.
                .filter(state -> state.getRouteCoordinates() != null
                        ? state.getFractionOnRoute() >= 0
                        : true)
                .toList();

        if (activeStates.isEmpty()) {
            return Mono.empty();
        }

        log.trace("Prediction cycle: {} moving vehicles", activeStates.size());

        return Flux.fromIterable(activeStates)
                .flatMap(state -> {
                    VehiclePredictionState advanced = advanceState(state);
                    vehicleStates.put(advanced.getVehicleId(), advanced);
                    return broadcastPrediction(advanced);
                })
                .then();
    }

    // ---- private ----

    private void cleanupStaleStates() {
        Instant cutoff = Instant.now().minusSeconds(300);
        vehicleStates.entrySet().removeIf(e -> {
            Instant received = e.getValue().getLastReceivedAt();
            return received != null && received.isBefore(cutoff);
        });
    }

    private boolean isAtRouteBoundary(VehiclePredictionState state) {
        if (state.getRouteCoordinates() == null || state.getFractionOnRoute() < 0) {
            return false;
        }
        double f = state.getFractionOnRoute();
        return (state.getDirection() == 0 && f >= 1.0)
                || (state.getDirection() == 1 && f <= 0.0);
    }

    private VehiclePredictionState advanceState(VehiclePredictionState state) {
        VehiclePredictionState advanced = advancePositionOnly(state);
        return applySmoothCorrection(advanced);
    }

    /** Advances the predicted position by one DT_SECONDS step (route-aware or dead-reckoning). */
    private VehiclePredictionState advancePositionOnly(VehiclePredictionState state) {
        double decayedSpeedKmh = state.getSpeedKmh() * properties.getDecayFactor();

        List<double[]> routeCoords = state.getRouteCoordinates();
        double totalRouteDistance = state.getTotalRouteDistanceMeters();

        if (routeCoords != null && state.getFractionOnRoute() >= 0 && totalRouteDistance > 0) {
            double speedMs = decayedSpeedKmh / 3.6;
            double fractionDelta = speedMs * DT_SECONDS / totalRouteDistance;

            double newFraction;
            if (state.getDirection() == 0) {
                newFraction = Math.min(state.getFractionOnRoute() + fractionDelta, 1.0);
            } else {
                newFraction = Math.max(state.getFractionOnRoute() - fractionDelta, 0.0);
            }

            double[] coords = mapMatchingService.interpolateRoutePoint(routeCoords, newFraction, totalRouteDistance);
            if (coords == null) return state;

            double newCourse = mapMatchingService.calculateCourseFromRoute(
                    routeCoords, newFraction, state.getDirection(), totalRouteDistance);

            return state.toBuilder()
                    .speedKmh(decayedSpeedKmh)
                    .predictedLatitude(coords[0])
                    .predictedLongitude(coords[1])
                    .fractionOnRoute(newFraction)
                    .course(newCourse)
                    .build();
        }

        // Dead-reckoning: advance along course heading
        double speedMs   = decayedSpeedKmh / 3.6;
        double courseRad = Math.toRadians(state.getCourse());
        double dNorth = speedMs * DT_SECONDS * Math.cos(courseRad);
        double dEast  = speedMs * DT_SECONDS * Math.sin(courseRad);
        double dLat   = dNorth / METRES_PER_DEGREE_LAT;
        double dLon   = dEast  / (METRES_PER_DEGREE_LAT * Math.cos(Math.toRadians(state.getPredictedLatitude())));

        return state.toBuilder()
                .speedKmh(decayedSpeedKmh)
                .predictedLatitude(state.getPredictedLatitude() + dLat)
                .predictedLongitude(state.getPredictedLongitude() + dLon)
                .build();
    }

    /**
     * Fix 3: Gradually blends the predicted position toward the GPS correction target.
     * Each cycle moves 1/N of the remaining distance, giving a smooth visual transition.
     */
    private VehiclePredictionState applySmoothCorrection(VehiclePredictionState state) {
        if (state.getCorrectionCyclesLeft() <= 0) return state;

        double alpha  = 1.0 / state.getCorrectionCyclesLeft();
        double corrLat = state.getPredictedLatitude()
                + alpha * (state.getCorrectionTargetLat() - state.getPredictedLatitude());
        double corrLon = state.getPredictedLongitude()
                + alpha * (state.getCorrectionTargetLon() - state.getPredictedLongitude());

        return state.toBuilder()
                .predictedLatitude(corrLat)
                .predictedLongitude(corrLon)
                .correctionCyclesLeft(state.getCorrectionCyclesLeft() - 1)
                .build();
    }

    private Mono<Void> broadcastPrediction(VehiclePredictionState state) {
        Double fractionValue = (state.getFractionOnRoute() >= 0) ? state.getFractionOnRoute() : null;

        VehiclePositionWebSocketMessage msg = new VehiclePositionWebSocketMessage(
                state.getVehicleId(),
                state.getLicensePlate(),
                state.getRouteNumber(),
                state.getPredictedLatitude(),
                state.getPredictedLongitude(),
                state.getSpeedKmh(),
                state.isInMotion(),
                LocalDateTime.now(),
                state.getCourse(),
                state.getDirection() == 0,
                null,
                Boolean.TRUE,
                fractionValue
        );

        return Mono.fromRunnable(() -> {
            try {
                directBroadcaster.broadcastDirect(msg);
                log.trace("Prediction broadcasted: vehicleId={}", state.getVehicleId());
            } catch (Exception e) {
                log.warn("Failed to broadcast prediction for vehicle {}: {}", state.getVehicleId(), e.getMessage());
            }
        });
    }

    /**
     * Returns a blended or accepted coordinate value (lat if {@code isLat=true}, lon otherwise).
     * Within correction distance → smooth blend; farther → accept GPS directly (route-unaware fallback).
     */
    private double blendOrAccept(VehiclePredictionState existing, double realLat, double realLon,
                                  boolean isLat) {
        if (existing == null) return isLat ? realLat : realLon;
        double dist = DistanceCalculationService.haversineDistanceMeters(
                existing.getPredictedLatitude(), existing.getPredictedLongitude(), realLat, realLon);
        double cf = properties.getCorrectionFactor();
        if (dist <= MAX_CORRECTION_DISTANCE_METERS) {
            return isLat
                    ? existing.getPredictedLatitude() + cf * (realLat - existing.getPredictedLatitude())
                    : existing.getPredictedLongitude() + cf * (realLon - existing.getPredictedLongitude());
        }
        return isLat ? realLat : realLon;
    }

    public int getActiveStateCount() {
        return vehicleStates.size();
    }
}
