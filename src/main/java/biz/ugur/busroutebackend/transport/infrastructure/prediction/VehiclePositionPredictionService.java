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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Service
@Slf4j
public class VehiclePositionPredictionService {

    private static final double METRES_PER_DEGREE_LAT = 111_320.0;
    private static final double DT_SECONDS = 1.0;

    private static final double MAX_CORRECTION_DISTANCE_METERS = 50.0;

    private static final double MAX_BUS_SPEED_MS = 33.0;

    private static final double OUTLIER_TOLERANCE = 1.0;
   
    private static final double MAX_TELEPORT_DISTANCE_METERS = 5_000.0;
    private static final double DIRECTION_FLIP_THRESHOLD_DEG = 90.0;
    private static final long MAX_GPS_AGE_MS = 10 * 60 * 1000L;
    private static final int OPPOSITE_SNAP_THRESHOLD = 3;

    private final ConcurrentHashMap<String, VehiclePredictionState> vehicleStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> consecutiveOppositeSnaps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> pendingDirectionFixes = new ConcurrentHashMap<>();

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

        long gpsAgeMs = Instant.now().toEpochMilli() - timestamp.toEpochMilli();
        if (gpsAgeMs > MAX_GPS_AGE_MS) {
            log.trace("Stale GPS rejected in prediction engine for vehicle {}: age={}min",
                    vehicleId, gpsAgeMs / 60_000);
            return;
        }

        VehiclePredictionState existing = vehicleStates.get(vehicleId);

        if (existing != null && !timestamp.isAfter(existing.getLastGpsUpdate())) {
            log.trace("Ignoring duplicate GPS for vehicle {}: timestamp {} <= lastGpsUpdate {}",
                    vehicleId, timestamp, existing.getLastGpsUpdate());
            return;
        }

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
                    vehicleStates.put(vehicleId, existing.toBuilder()
                            .lastGpsUpdate(timestamp)
                            .lastReceivedAt(Instant.now())
                            .gpsLatitude(latitude)
                            .gpsLongitude(longitude)
                            .build());
                    return;
                }
            } else if (elapsedMs >= 300_000) {
                double distFromLastGps = DistanceCalculationService.haversineDistanceMeters(
                        existing.getGpsLatitude(), existing.getGpsLongitude(), latitude, longitude);
                if (distFromLastGps > MAX_TELEPORT_DISTANCE_METERS) {
                    log.warn("GPS teleportation rejected for vehicle {} after {}min gap: {}m (max {}m)",
                            vehicleId, elapsedMs / 60_000, (int) distFromLastGps,
                            (int) MAX_TELEPORT_DISTANCE_METERS);
                    vehicleStates.put(vehicleId, existing.toBuilder()
                            .lastGpsUpdate(timestamp)
                            .lastReceivedAt(Instant.now())
                            .build());
                    return;
                }
            }
        }

        double predictedLat;
        double predictedLon;
        double fraction;

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
            log.debug("[GPS_PIPELINE] SNAP_ATTEMPT vehicle={} route={} dir={} lat={} lon={}",
                    vehicleId, routeNumber, direction, latitude, longitude);
            MapMatchingService.SnappedResult snap = (existing != null && existing.getLastGpsFraction() >= 0)
                    ? mapMatchingService.snapToNearestSegment(latitude, longitude, routeCoords, totalDist,
                            existing.getLastGpsFraction(), 0.20)
                    : mapMatchingService.snapToNearestSegment(latitude, longitude, routeCoords, totalDist);

            boolean headingCorrected = false;

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
                            headingCorrected = true;
                        }
                    }
                }
            }

            if (snap.snapped()) {
                log.debug("[GPS_PIPELINE] SNAP_OK vehicle={} route={} dist={}m frac={}",
                        vehicleId, routeNumber,
                        String.format("%.1f", snap.distanceMeters()),
                        String.format("%.4f", snap.fraction()));
                consecutiveOppositeSnaps.remove(vehicleId);
                double realFraction = snap.fraction();
                double predictedFraction = (existing != null) ? existing.getFractionOnRoute() : -1;

                if (!headingCorrected && existing != null && existing.getLastGpsFraction() >= 0) {
                    double lastGpsFrac = existing.getLastGpsFraction();
                    double fracDelta = realFraction - lastGpsFrac;
                    boolean gpsFracIncreasing = fracDelta > 0.005;
                    boolean gpsFracDecreasing = fracDelta < -0.005;
                    boolean gpsMoveAgainstDir = (direction == 0 && gpsFracDecreasing)
                            || (direction == 1 && gpsFracIncreasing);
                    boolean plausibleJump = Math.abs(fracDelta) <= 0.25;

                    if (gpsMoveAgainstDir && plausibleJump) {
                        int correctedDir = (direction == 0) ? 1 : 0;
                        List<double[]> correctedCoords = routeGeometryCache.getPoints(routeNumber, correctedDir);
                        if (correctedCoords != null) {
                            double correctedDist = routeGeometryCache.getTotalDistance(routeNumber, correctedDir);
                            MapMatchingService.SnappedResult correctedSnap =
                                    mapMatchingService.snapToNearestSegment(latitude, longitude, correctedCoords, correctedDist);
                            if (correctedSnap.snapped()) {
                                log.info("[GPS_PIPELINE] DIR_CORRECT_FRAC vehicle={} route={} dir={}→{} gpsFrac={}→{} (delta={})",
                                        vehicleId, routeNumber, direction, correctedDir,
                                        String.format("%.4f", lastGpsFrac),
                                        String.format("%.4f", realFraction),
                                        String.format("%.4f", fracDelta));
                                direction = correctedDir;
                                routeCoords = correctedCoords;
                                totalDist = correctedDist;
                                snap = correctedSnap;
                                realFraction = snap.fraction();
                            }
                        }
                    } else if (gpsMoveAgainstDir) {
                        log.debug("[GPS_PIPELINE] DIR_CORRECT_FRAC_SKIP vehicle={} route={} dir={} delta={} (jump too large or heading corrected)",
                                vehicleId, routeNumber, direction, String.format("%.4f", fracDelta));
                    }
                }

                boolean plausibleSnap = true;
                if (!headingCorrected && existing != null && existing.getLastGpsFraction() >= 0) {
                    double jumpSize = Math.abs(realFraction - existing.getLastGpsFraction());
                    if (jumpSize > 0.25) {
                        plausibleSnap = false;
                        log.debug("[GPS_PIPELINE] SNAP_IMPLAUSIBLE vehicle={} route={} dir={} lastFrac={}→newFrac={} jump={} — keeping predicted",
                                vehicleId, routeNumber, direction,
                                String.format("%.4f", existing.getLastGpsFraction()),
                                String.format("%.4f", realFraction),
                                String.format("%.4f", jumpSize));
                    }
                }

                boolean realIsAhead = plausibleSnap && (
                        (predictedFraction < 0)
                        || (direction == 0 && realFraction >= predictedFraction)
                        || (direction == 1 && realFraction <= predictedFraction));

                // No distance-based override here.
                // Accepting GPS when it is behind predicted causes backward teleportation
                // for fast buses (prediction advances 150m+ per 5-second GPS cycle).
                // Direction changes are handled by DIR_CORRECT_FRAC and heading correction.
                // Route-end wrap-around is handled by isAtRouteBoundary (resets fraction to -1).

                if (realIsAhead) {
                    predictedLat = snap.latitude();
                    predictedLon = snap.longitude();
                    fraction = realFraction;
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
                int oppositeDir = (direction == 0) ? 1 : 0;
                List<double[]> oppositeCoords = routeGeometryCache.getPoints(routeNumber, oppositeDir);
                if (oppositeCoords != null) {
                    double oppositeDist = routeGeometryCache.getTotalDistance(routeNumber, oppositeDir);
                    MapMatchingService.SnappedResult oppositeSnap =
                            mapMatchingService.snapToNearestSegment(latitude, longitude, oppositeCoords, oppositeDist);
                    if (oppositeSnap.snapped()) {
                        log.debug("[GPS_PIPELINE] SNAP_OPPOSITE vehicle={} route={} dir={}→{} dist={}m (primary={}m)",
                                vehicleId, routeNumber, direction, oppositeDir,
                                String.format("%.1f", oppositeSnap.distanceMeters()),
                                String.format("%.1f", snap.distanceMeters()));
                        int snapCount = consecutiveOppositeSnaps.merge(vehicleId, 1, Integer::sum);
                        if (snapCount >= OPPOSITE_SNAP_THRESHOLD) {
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
                        course = mapMatchingService.calculateCourseFromRoute(routeCoords, fraction, direction, totalDist);
                    } else {
                        log.debug("[GPS_PIPELINE] SNAP_FAIL vehicle={} route={} dist={}m (opposite={}m) > threshold={}m → dead-reckoning",
                                vehicleId, routeNumber,
                                String.format("%.1f", snap.distanceMeters()),
                                String.format("%.1f", oppositeSnap.distanceMeters()),
                                (int) MapMatchingService.MAX_SNAP_DISTANCE_METERS);
                        consecutiveOppositeSnaps.remove(vehicleId);
                        predictedLat = blendOrAccept(existing, latitude, longitude, true);
                        predictedLon = blendOrAccept(existing, latitude, longitude, false);
                        fraction = -1;
                        routeCoords = null;
                        totalDist = 0;
                    }
                } else {
                    log.debug("[GPS_PIPELINE] SNAP_FAIL vehicle={} route={} dist={}m > threshold={}m → dead-reckoning",
                            vehicleId, routeNumber,
                            String.format("%.1f", snap.distanceMeters()),
                            (int) MapMatchingService.MAX_SNAP_DISTANCE_METERS);
                    consecutiveOppositeSnaps.remove(vehicleId);
                    predictedLat = blendOrAccept(existing, latitude, longitude, true);
                    predictedLon = blendOrAccept(existing, latitude, longitude, false);
                    fraction = -1;
                    routeCoords = null;
                    totalDist = 0;
                }
            }

        } else {
            log.debug("[GPS_PIPELINE] SNAP_SKIP vehicle={} snapToRoute={} routeNumber={}",
                    vehicleId, properties.isSnapToRoute(), routeNumber);
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
                .lastGpsFraction(fraction)  
                .direction(direction);

        vehicleStates.put(vehicleId, builder.lastReceivedAt(Instant.now()).build());
        log.debug("[GPS_PIPELINE] GPS_STORED vehicle={} plate={} route={} speed={}km/h inMotion={} frac={} mode={}",
                vehicleId, licensePlate, routeNumber,
                String.format("%.1f", speedKmh), inMotion,
                fraction >= 0 ? String.format("%.4f", fraction) : "-",
                fraction >= 0 ? "SNAPPED" : "DEAD_RECKONING");
    }

    public Mono<Void> predictNextPositions() {
        if (!properties.isEnabled()) {
            return Mono.empty();
        }

        cleanupStaleStates();

        Instant now = Instant.now();
        long maxAgeMs = properties.getMaxAgeMs();
        double minSpeed = properties.getMinSpeedKmh();

        vehicleStates.values().forEach(state -> {
            if (isAtRouteBoundary(state)) {
                vehicleStates.put(state.getVehicleId(),
                        state.toBuilder().fractionOnRoute(-1).build());
            }
        });

        List<VehiclePredictionState> activeStates = vehicleStates.values().stream()
                .filter(state -> state.isInMotion() && state.getSpeedKmh() >= minSpeed)
                .filter(state -> state.getLastReceivedAt() != null
                        && (now.toEpochMilli() - state.getLastReceivedAt().toEpochMilli()) <= maxAgeMs)
                .filter(state -> state.getRouteCoordinates() != null
                        ? state.getFractionOnRoute() >= 0
                        : true)
                .toList();

        if (activeStates.isEmpty()) {
            return Mono.empty();
        }

        long snapped = activeStates.stream().filter(s -> s.getFractionOnRoute() >= 0).count();
        long dr = activeStates.size() - snapped;
        log.debug("[GPS_PIPELINE] PRED_CYCLE total={} snapped={} dead_reckoning={}", activeStates.size(), snapped, dr);

        return Flux.fromIterable(activeStates)
                .flatMap(state -> {
                    VehiclePredictionState advanced = advanceState(state);
                    vehicleStates.put(advanced.getVehicleId(), advanced);
                    return broadcastPrediction(advanced);
                })
                .then();
    }


    private void cleanupStaleStates() {
        Instant cutoff = Instant.now().minusSeconds(300);
        vehicleStates.entrySet().removeIf(e -> {
            Instant received = e.getValue().getLastReceivedAt();
            return received != null && received.isBefore(cutoff);
        });
        consecutiveOppositeSnaps.keySet().retainAll(vehicleStates.keySet());
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
        return advancePositionOnly(state);
    }

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

        log.debug("[GPS_PIPELINE] PRED_DR vehicle={} course={}° speed={}km/h",
                state.getVehicleId(),
                String.format("%.1f", state.getCourse()),
                String.format("%.1f", decayedSpeedKmh));
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
                log.debug("[GPS_PIPELINE] WS_PRED vehicle={} plate={} mode={} frac={} lat={} lon={} speed={}km/h",
                        state.getVehicleId(), state.getLicensePlate(),
                        fractionValue != null ? "SNAPPED" : "DEAD_RECKONING",
                        fractionValue != null ? String.format("%.4f", fractionValue) : "-",
                        String.format("%.6f", state.getPredictedLatitude()),
                        String.format("%.6f", state.getPredictedLongitude()),
                        String.format("%.1f", state.getSpeedKmh()));
            } catch (Exception e) {
                log.warn("Failed to broadcast prediction for vehicle {}: {}", state.getVehicleId(), e.getMessage());
            }
        });
    }

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

  
    public boolean hasActiveState(String vehicleId) {
        if (!properties.isEnabled()) {
            return false;
        }
        VehiclePredictionState state = vehicleStates.get(vehicleId);
        if (state == null) {
            return false;
        }
        long ageMs = Instant.now().toEpochMilli()
                - (state.getLastReceivedAt() != null ? state.getLastReceivedAt().toEpochMilli() : 0);
        return ageMs <= properties.getMaxAgeMs();
    }

    public int getActiveStateCount() {
        return vehicleStates.size();
    }

    public Map<String, Integer> drainPendingDirectionFixes() {
        if (pendingDirectionFixes.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> result = new java.util.HashMap<>(pendingDirectionFixes);
        pendingDirectionFixes.clear();
        return result;
    }
}
