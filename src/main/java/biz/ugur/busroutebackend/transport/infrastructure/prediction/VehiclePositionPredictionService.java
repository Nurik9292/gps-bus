package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TimePeriod;
import biz.ugur.busroutebackend.routing.infrastructure.config.ETAProperties;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.DirectVehiclePositionBroadcaster;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage.NextStopEta;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
    /** Track last broadcast position per vehicle to detect WS-visible teleports. */
    private final ConcurrentHashMap<String, double[]> lastBroadcastPosition = new ConcurrentHashMap<>();

    private final PredictionProperties properties;
    private final DirectVehiclePositionBroadcaster directBroadcaster;
    private final RouteGeometryCache routeGeometryCache;
    private final MapMatchingService mapMatchingService;
    private final VehiclePredictionStateRepository stateRepository;
    private final ETAProperties etaProperties;

    public VehiclePositionPredictionService(PredictionProperties properties,
                                             DirectVehiclePositionBroadcaster directBroadcaster,
                                             RouteGeometryCache routeGeometryCache,
                                             MapMatchingService mapMatchingService,
                                             VehiclePredictionStateRepository stateRepository,
                                             ETAProperties etaProperties) {
        this.properties = properties;
        this.directBroadcaster = directBroadcaster;
        this.routeGeometryCache = routeGeometryCache;
        this.mapMatchingService = mapMatchingService;
        this.stateRepository = stateRepository;
        this.etaProperties = etaProperties;
    }

    @PostConstruct
    public void restoreFromRedis() {
        if (!properties.isEnabled()) return;

        stateRepository.loadAll()
                .filter(state -> state.getVehicleId() != null)
                .map(state -> {
                    // Reload route geometry from in-memory cache (it may not be loaded yet,
                    // in which case routeCoordinates stays null and prediction falls back to dead-reckoning
                    // until the next real GPS update arrives).
                    if (state.getRouteNumber() != null) {
                        List<double[]> coords = routeGeometryCache.getPoints(
                                state.getRouteNumber(), state.getDirection());
                        double totalDist = routeGeometryCache.getTotalDistance(
                                state.getRouteNumber(), state.getDirection());
                        if (coords != null) {
                            return state.toBuilder()
                                    .routeCoordinates(coords)
                                    .totalRouteDistanceMeters(totalDist)
                                    .build();
                        }
                    }
                    return state;
                })
                // Mark restored states as stale so they are NOT broadcast until the first
                // fresh GPS arrives. Without this, prediction would broadcast old Redis positions
                // at startup → then snap to real GPS 6s later → visible teleport across the city.
                // Setting lastReceivedAt far in the past makes the maxAgeMs filter exclude them.
                .map(state -> state.toBuilder()
                        .lastReceivedAt(Instant.now().minusSeconds(properties.getMaxAgeMs() / 1000 + 60))
                        .build())
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        state -> {
                            vehicleStates.put(state.getVehicleId(), state);
                            log.debug("Restored prediction state (stale, awaiting fresh GPS): vehicle={} route={} frac={}",
                                    state.getVehicleId(), state.getRouteNumber(),
                                    state.getFractionOnRoute() >= 0
                                            ? String.format("%.4f", state.getFractionOnRoute()) : "-");
                        },
                        err -> log.warn("Error restoring prediction states: {}", err.getMessage()),
                        () -> log.info("Prediction states restored from Redis: {} vehicles (awaiting fresh GPS before broadcast)",
                                vehicleStates.size())
                );
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
        double newRejectedFrac = existing != null ? existing.getLastRejectedGpsFraction() : -1;
        int newImplausibleCount = existing != null ? existing.getConsecutiveImplausibleCount() : 0;

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
            boolean fracCorrected = false;

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
                    // Fraction always increases: "against direction" means GPS fraction is decreasing.
                    boolean gpsMoveAgainstDir = gpsFracDecreasing;
                    boolean plausibleJump = Math.abs(fracDelta) <= 0.25;

                    if (gpsMoveAgainstDir && plausibleJump) {
                        int correctedDir = (direction == 0) ? 1 : 0;
                        List<double[]> correctedCoords = routeGeometryCache.getPoints(routeNumber, correctedDir);
                        if (correctedCoords != null) {
                            double correctedDist = routeGeometryCache.getTotalDistance(routeNumber, correctedDir);
                            MapMatchingService.SnappedResult correctedSnap =
                                    mapMatchingService.snapToNearestSegment(latitude, longitude, correctedCoords, correctedDist);
                            if (correctedSnap.snapped()
                                    && isDirectionFlipPhysicallyPlausible(vehicleId, "FRAC", existing,
                                            correctedSnap, lastGpsFrac)) {
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
                    // Route reassignment — fractions from different geometries are incomparable.
                    // Reset rejection tracking so SNAP_IMPLAUSIBLE doesn't block the correct new position.
                    newRejectedFrac = -1;
                    newImplausibleCount = 0;
                    log.debug("[GPS_PIPELINE] ROUTE_CHANGE vehicle={} route={}→{} frac={} — resetting snap state",
                            vehicleId, existing.getRouteNumber(), routeNumber,
                            String.format("%.4f", realFraction));
                }

                if (!headingCorrected && !fracCorrected && !routeChanged && existing != null && existing.getLastGpsFraction() >= 0) {
                    double jumpSize = Math.abs(realFraction - existing.getLastGpsFraction());
                    if (jumpSize > 0.25) {
                        // Check if GPS is consistently reporting the same location across multiple updates.
                        // If so, the vehicle physically moved there — force-accept after threshold.
                        boolean sameRejectedLocation = existing.getLastRejectedGpsFraction() >= 0
                                && Math.abs(realFraction - existing.getLastRejectedGpsFraction()) < 0.05;
                        if (sameRejectedLocation) {
                            newImplausibleCount = existing.getConsecutiveImplausibleCount() + 1;
                        } else {
                            newImplausibleCount = 1;
                        }
                        newRejectedFrac = realFraction;

                        if (newImplausibleCount >= 3) {
                            // Vehicle has consistently reported a far-away position.
                            // Instead of force-accepting (which causes km-scale teleports),
                            // reset to dead-reckoning at the actual GPS position.
                            // Next GPS update will attempt a fresh snap without lastGpsFraction bias.
                            log.info("[GPS_PIPELINE] SNAP_IMPLAUSIBLE_RESET vehicle={} route={} dir={} frac={}→{} jump={} ({}x) — resetting to dead-reckoning at GPS position",
                                    vehicleId, routeNumber, direction,
                                    String.format("%.4f", existing.getLastGpsFraction()),
                                    String.format("%.4f", realFraction),
                                    String.format("%.4f", jumpSize),
                                    newImplausibleCount);
                            newImplausibleCount = 0;
                            newRejectedFrac = -1;
                            resetToDR = true;
                        } else {
                            plausibleSnap = false;
                            log.debug("[GPS_PIPELINE] SNAP_IMPLAUSIBLE vehicle={} route={} dir={} lastFrac={}→newFrac={} jump={} ({}/3) — keeping predicted",
                                    vehicleId, routeNumber, direction,
                                    String.format("%.4f", existing.getLastGpsFraction()),
                                    String.format("%.4f", realFraction),
                                    String.format("%.4f", jumpSize),
                                    newImplausibleCount);
                        }
                    } else {
                        // Normal snap — clear rejection tracking
                        newRejectedFrac = -1;
                        newImplausibleCount = 0;
                    }
                }

                // Fraction always increases (both forward and backward geometries are traversed 0→1).
                // When direction was corrected (headingCorrected or fracCorrected), the new
                // fraction is on a DIFFERENT geometry — comparing with old fraction is meaningless.
                // Always accept the snapped position after direction change.
                boolean realIsAhead = headingCorrected || fracCorrected
                        || (plausibleSnap && (predictedFraction < 0 || realFraction >= predictedFraction));

                if (headingCorrected || fracCorrected) {
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

                if (realIsAhead) {
                    predictedLat = snap.latitude();
                    predictedLon = snap.longitude();
                    fraction = realFraction;
                    course = mapMatchingService.calculateCourseFromRoute(routeCoords, fraction, direction, totalDist);
                    newRejectedFrac = -1;
                    newImplausibleCount = 0;
                } else {
                    predictedLat = existing.getPredictedLatitude();
                    predictedLon = existing.getPredictedLongitude();
                    fraction = existing.getFractionOnRoute();
                    course = mapMatchingService.calculateCourseFromRoute(routeCoords, fraction, direction, totalDist);
                    log.trace("GPS behind predicted for vehicle {} (real={}, predicted={}); keeping predicted",
                            vehicleId, realFraction, predictedFraction);
                }

                // After 3+ consecutive implausible snaps, reset to dead-reckoning at raw GPS position.
                // The bus is consistently at a location that doesn't match the current route/direction
                // state. Instead of force-accepting (which teleports km-scale), we drop route-snap
                // and place the marker at the actual GPS position. Next GPS will attempt a fresh snap.
                if (resetToDR) {
                    predictedLat = latitude;
                    predictedLon = longitude;
                    fraction = -1;
                    routeCoords = null;
                    totalDist = 0;
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

        // Instant snap: prediction position = snapped GPS position.
        // Log teleports (large jumps) for diagnostics.
        if (existing != null
                && existing.getPredictedLatitude() != 0.0
                && existing.getPredictedLongitude() != 0.0) {
            double distFromPredicted = DistanceCalculationService.haversineDistanceMeters(
                    existing.getPredictedLatitude(), existing.getPredictedLongitude(),
                    predictedLat, predictedLon);
            if (distFromPredicted > properties.getTeleportThresholdMeters()) {
                log.info("[GPS_PIPELINE] SNAP_TELEPORT vehicle={} plate={} dist={}m — large correction",
                        vehicleId, licensePlate, String.format("%.0f", distFromPredicted));
            }
        }

        VehiclePredictionState.VehiclePredictionStateBuilder builder = VehiclePredictionState.builder()
                .vehicleId(vehicleId)
                .licensePlate(licensePlate)
                .routeNumber(routeNumber)
                .gpsLatitude(latitude)
                .gpsLongitude(longitude)
                .speedKmh(computeSmoothedSpeed(existing, speedKmh))
                .rawGpsSpeedKmh(speedKmh)
                .smoothedSpeedKmh(computeSmoothedSpeed(existing, speedKmh))
                .recentSpeeds(appendSpeedToBuffer(existing, speedKmh))
                .course(course)
                .inMotion(inMotion)
                .lastGpsUpdate(timestamp)
                .predictedLatitude(predictedLat)
                .predictedLongitude(predictedLon)
                // Clear dwell if GPS shows movement
                .dwellStartedAt(speedKmh >= properties.getDwellSpeedThresholdKmh() ? null
                        : (existing != null ? existing.getDwellStartedAt() : null))
                .dwellStopFraction(speedKmh >= properties.getDwellSpeedThresholdKmh() ? -1
                        : (existing != null ? existing.getDwellStopFraction() : -1))
                .routeCoordinates(routeCoords)
                .totalRouteDistanceMeters(totalDist)
                .fractionOnRoute(fraction)
                .lastGpsFraction(fraction)
                .lastRejectedGpsFraction(newRejectedFrac)
                .consecutiveImplausibleCount(newImplausibleCount)
                .direction(direction);

        VehiclePredictionState builtState = builder.lastReceivedAt(Instant.now()).build();
        vehicleStates.put(vehicleId, builtState);
        log.debug("[GPS_PIPELINE] GPS_STORED vehicle={} plate={} route={} speed={}km/h inMotion={} frac={} mode={}",
                vehicleId, licensePlate, routeNumber,
                String.format("%.1f", speedKmh), inMotion,
                fraction >= 0 ? String.format("%.4f", fraction) : "-",
                fraction >= 0 ? "SNAPPED" : "DEAD_RECKONING");
        stateRepository.save(builtState)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(null, err -> log.debug("Redis state save failed for {}: {}", vehicleId, err.getMessage()));
    }

    public Mono<Void> predictNextPositions() {
        if (!properties.isEnabled()) {
            return Mono.empty();
        }

        cleanupStaleStates();

        Instant now = Instant.now();
        long maxAgeMs = properties.getMaxAgeMs();
        double minSpeed = properties.getMinSpeedKmh();
        long stoppedIntervalMs = properties.getStoppedBroadcastIntervalMs();

        vehicleStates.values().forEach(state -> {
            if (isAtRouteBoundary(state)) {
                vehicleStates.put(state.getVehicleId(),
                        state.toBuilder().fractionOnRoute(-1).build());
            }
        });

        // Moving vehicles: advance position conservatively and broadcast every tick.
        List<VehiclePredictionState> movingStates = vehicleStates.values().stream()
                .filter(state -> state.isInMotion() && state.getSpeedKmh() >= minSpeed)
                .filter(state -> state.getLastReceivedAt() != null
                        && (now.toEpochMilli() - state.getLastReceivedAt().toEpochMilli()) <= maxAgeMs)
                .filter(state -> state.getRouteCoordinates() != null
                        ? state.getFractionOnRoute() >= 0
                        : true)
                .toList();

        // Stopped vehicles: broadcast at reduced frequency to keep Flutter markers alive.
        // Without Path B (raw GPS broadcasts), stopped vehicles would disappear from the map.
        List<VehiclePredictionState> stoppedStates = vehicleStates.values().stream()
                .filter(state -> !state.isInMotion() || state.getSpeedKmh() < minSpeed)
                .filter(state -> state.getLastReceivedAt() != null
                        && (now.toEpochMilli() - state.getLastReceivedAt().toEpochMilli()) <= maxAgeMs)
                .filter(state -> {
                    Instant lastBroadcast = state.getLastBroadcastAt();
                    return lastBroadcast == null
                            || (now.toEpochMilli() - lastBroadcast.toEpochMilli()) >= stoppedIntervalMs;
                })
                .toList();

        if (movingStates.isEmpty() && stoppedStates.isEmpty()) {
            return Mono.empty();
        }

        long snapped = movingStates.stream().filter(s -> s.getFractionOnRoute() >= 0).count();
        long dr = movingStates.size() - snapped;
        log.debug("[GPS_PIPELINE] PRED_CYCLE moving={} (snapped={} dr={}) stopped={}",
                movingStates.size(), snapped, dr, stoppedStates.size());

        Mono<Void> movingMono = Flux.fromIterable(movingStates)
                .flatMap(state -> {
                    VehiclePredictionState advanced = advanceState(state);
                    advanced = advanced.toBuilder().lastBroadcastAt(now).build();
                    vehicleStates.put(advanced.getVehicleId(), advanced);
                    return broadcastPrediction(advanced);
                })
                .then();

        Mono<Void> stoppedMono = Flux.fromIterable(stoppedStates)
                .flatMap(state -> {
                    VehiclePredictionState marked = state.toBuilder().lastBroadcastAt(now).build();
                    vehicleStates.put(marked.getVehicleId(), marked);
                    return broadcastPrediction(marked);
                })
                .then();

        return movingMono.then(stoppedMono);
    }


    private void cleanupStaleStates() {
        Instant cutoff = Instant.now().minusSeconds(300);
        vehicleStates.entrySet().removeIf(e -> {
            Instant received = e.getValue().getLastReceivedAt();
            boolean stale = received != null && received.isBefore(cutoff);
            if (stale) {
                stateRepository.delete(e.getKey())
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe();
            }
            return stale;
        });
        consecutiveOppositeSnaps.keySet().retainAll(vehicleStates.keySet());
    }

    private boolean isAtRouteBoundary(VehiclePredictionState state) {
        if (state.getRouteCoordinates() == null || state.getFractionOnRoute() < 0) {
            return false;
        }
        // Fraction always increases toward 1.0 for both directions.
        return state.getFractionOnRoute() >= 1.0;
    }

    private VehiclePredictionState advanceState(VehiclePredictionState state) {
        return advancePositionOnly(state);
    }

    private VehiclePredictionState advancePositionOnly(VehiclePredictionState state) {
        // Conservative speed: prediction moves slower than reported GPS speed.
        // This guarantees "always lag, never lead" — the marker stays behind reality,
        // so when fresh GPS arrives the snap is always FORWARD (natural-looking).
        double conservativeFactor = properties.getConservativeSpeedFactor();

        // Adaptive decay: the longer since last GPS, the more aggressively we slow down.
        // 0-aggressiveDecayAfterMs: normal decay (0.98/tick)
        // aggressiveDecayAfterMs-stopAdvanceAfterMs: aggressive decay (0.90/tick)
        // >stopAdvanceAfterMs: STOP advancing entirely (marker freezes)
        long msSinceGps = state.getLastReceivedAt() != null
                ? Instant.now().toEpochMilli() - state.getLastReceivedAt().toEpochMilli()
                : 0;

        if (msSinceGps > properties.getStopAdvanceAfterMs()) {
            // No GPS for too long — freeze the marker
            return state;
        }

        // Three-tier decay: fresh (no decay) → normal decay → aggressive decay → freeze
        // 0 to freshGpsWindowMs (6s): constant speed, no decay — GPS is still fresh
        // freshGpsWindowMs to aggressiveDecayAfterMs (6-10s): normal decay 0.98/tick
        // aggressiveDecayAfterMs to stopAdvanceAfterMs (10-20s): aggressive decay 0.90/tick
        double decayFactor;
        if (msSinceGps <= properties.getFreshGpsWindowMs()) {
            decayFactor = 1.0; // no decay while GPS is fresh
        } else if (msSinceGps <= properties.getAggressiveDecayAfterMs()) {
            decayFactor = properties.getDecayFactor();
        } else {
            decayFactor = properties.getAggressiveDecayFactor();
        }

        double decayedSpeedKmh = state.getSpeedKmh() * decayFactor;

        // Near-stop precision: when the vehicle is close to the next stop,
        // increase conservative factor toward 1.0 (marker tracks GPS more closely).
        // Users at the stop need accurate visual position, not smooth-but-lagging.
        double adjustedConservative = conservativeFactor;
        List<double[]> routeCoords = state.getRouteCoordinates();
        double totalRouteDistance = state.getTotalRouteDistanceMeters();

        if (routeCoords != null && state.getFractionOnRoute() >= 0 && totalRouteDistance > 0) {
            double distToNextStop = computeDistanceToNextStop(state, totalRouteDistance);
            if (distToNextStop >= 0 && distToNextStop < 300.0) {
                // Linearly interpolate: at 300m conservative=0.85, at 0m conservative=1.0
                adjustedConservative = conservativeFactor + (1.0 - conservativeFactor) * (1.0 - distToNextStop / 300.0);
            }
        }

        double effectiveSpeedKmh = decayedSpeedKmh * adjustedConservative;

        if (routeCoords != null && state.getFractionOnRoute() >= 0 && totalRouteDistance > 0) {
            // Dwell check: if the vehicle is currently dwelling at a stop, freeze prediction
            // until dwell time expires or fresh GPS shows movement (speed > threshold).
            if (state.getDwellStartedAt() != null) {
                long dwellMs = Instant.now().toEpochMilli() - state.getDwellStartedAt().toEpochMilli();
                boolean dwellExpired = dwellMs >= (long)(properties.getDwellTimeSeconds() * 1000);
                boolean gpsShowsMovement = state.getRawGpsSpeedKmh() >= properties.getDwellSpeedThresholdKmh();
                if (!dwellExpired && !gpsShowsMovement) {
                    // Still dwelling — don't advance
                    return state;
                }
                // Dwell ended — clear dwell state and continue advancing
                return state.toBuilder()
                        .dwellStartedAt(null)
                        .dwellStopFraction(-1)
                        .build();
            }

            double stopFactor = computeStopDecelerationFactor(state, totalRouteDistance);
            double speedMs = (effectiveSpeedKmh * stopFactor) / 3.6;
            double fractionDelta = speedMs * DT_SECONDS / totalRouteDistance;

            double newFraction = Math.min(state.getFractionOnRoute() + fractionDelta, 1.0);

            // Check if prediction crossed a stop — enter dwell mode
            double distToNextStop = computeDistanceToNextStop(state, totalRouteDistance);
            if (distToNextStop >= 0 && distToNextStop < properties.getDwellActivationDistanceMeters()
                    && state.getRawGpsSpeedKmh() < properties.getDwellSpeedThresholdKmh()) {
                double[] stopFractions = routeGeometryCache.getStopFractions(state.getRouteNumber(), state.getDirection());
                double nextStopFrac = stopFractions != null
                        ? findNextStopFraction(stopFractions, state.getFractionOnRoute(), state.getDirection())
                        : -1;
                if (nextStopFrac >= 0) {
                    log.debug("[GPS_PIPELINE] DWELL_START vehicle={} plate={} stop_frac={} dist={}m",
                            state.getVehicleId(), state.getLicensePlate(),
                            String.format("%.4f", nextStopFrac),
                            String.format("%.0f", distToNextStop));
                    // Snap to the stop and freeze
                    double[] stopCoords = mapMatchingService.interpolateRoutePoint(routeCoords, nextStopFrac, totalRouteDistance);
                    if (stopCoords != null) {
                        return state.toBuilder()
                                .predictedLatitude(stopCoords[0])
                                .predictedLongitude(stopCoords[1])
                                .fractionOnRoute(nextStopFrac)
                                .dwellStartedAt(Instant.now())
                                .dwellStopFraction(nextStopFrac)
                                .speedKmh(0)
                                .build();
                    }
                }
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

        // Dead-reckoning fallback (no route geometry)
        double speedMs = effectiveSpeedKmh / 3.6;
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

    private boolean isDirectionFlipPhysicallyPlausible(String vehicleId, String trigger,
                                                        VehiclePredictionState existing,
                                                        MapMatchingService.SnappedResult flippedSnap,
                                                        double curFraction) {
        if (existing == null || existing.getPredictedLatitude() == 0.0 || existing.getPredictedLongitude() == 0.0) {
            return true;
        }

        double tolerance = properties.getTerminalFractionTolerance();

        // Check BOTH current snap fraction AND existing predicted fraction for terminal proximity.
        // Bus turnaround at terminal: predicted frac ≈ 1.0 (end of forward), GPS snaps to frac ≈ 0.0
        // on the opposite direction. Either condition means "near terminal → flip is legitimate".
        boolean curNearTerminal = curFraction >= 0
                && (curFraction <= tolerance || curFraction >= (1.0 - tolerance));
        // Check both fractionOnRoute and lastGpsFraction — isAtRouteBoundary resets
        // fractionOnRoute to -1 at boundary, but lastGpsFraction still has the last value.
        double predFrac = existing.getFractionOnRoute() >= 0
                ? existing.getFractionOnRoute()
                : existing.getLastGpsFraction();
        boolean predNearTerminal = predFrac >= 0
                && (predFrac <= tolerance || predFrac >= (1.0 - tolerance));
        if (curNearTerminal || predNearTerminal) {
            log.debug("[GPS_PIPELINE] DIR_FLIP_ALLOWED_TERMINAL vehicle={} trigger={} curFrac={} predFrac={}",
                    vehicleId, trigger,
                    curFraction >= 0 ? String.format("%.4f", curFraction) : "-",
                    existing.getFractionOnRoute() >= 0 ? String.format("%.4f", existing.getFractionOnRoute()) : "-");
            return true;
        }

        double physicalJumpMeters = DistanceCalculationService.haversineDistanceMeters(
                existing.getPredictedLatitude(), existing.getPredictedLongitude(),
                flippedSnap.latitude(), flippedSnap.longitude());

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

    private Mono<Void> broadcastPrediction(VehiclePredictionState state) {
        Double fractionValue = (state.getFractionOnRoute() >= 0) ? state.getFractionOnRoute() : null;
        List<NextStopEta> nextStops = computeNextStopsEta(state, 3);

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
                nextStops.isEmpty() ? null : nextStops,
                Boolean.TRUE,
                fractionValue,
                computeConfidence(state).name()
        );

        return Mono.fromRunnable(() -> {
            try {
                // Final teleport guard: if this broadcast would move the marker >500m,
                // suppress it. The marker stays at the last known good position.
                // This prevents ALL visible teleports regardless of cause (direction bounce,
                // snap ambiguity, route geometry issues, GPS outliers).
                double[] prevPos = lastBroadcastPosition.get(state.getVehicleId());
                if (prevPos != null) {
                    double jumpDist = DistanceCalculationService.haversineDistanceMeters(
                            prevPos[0], prevPos[1],
                            state.getPredictedLatitude(), state.getPredictedLongitude());
                    if (jumpDist > properties.getTeleportThresholdMeters()) {
                        log.warn("[GPS_PIPELINE] WS_TELEPORT_SUPPRESSED vehicle={} plate={} dist={}m " +
                                        "from=({},{}) to=({},{}) frac={} dir={} — broadcast skipped",
                                state.getVehicleId(), state.getLicensePlate(),
                                String.format("%.0f", jumpDist),
                                String.format("%.5f", prevPos[0]),
                                String.format("%.5f", prevPos[1]),
                                String.format("%.5f", state.getPredictedLatitude()),
                                String.format("%.5f", state.getPredictedLongitude()),
                                fractionValue != null ? String.format("%.4f", fractionValue) : "-",
                                state.getDirection());
                        // Do NOT update lastBroadcastPosition here.
                        // Flutter still shows the OLD position — next broadcast must be
                        // compared against what Flutter actually sees, not what we suppressed.
                        return;
                    }
                }
                lastBroadcastPosition.put(state.getVehicleId(),
                        new double[]{state.getPredictedLatitude(), state.getPredictedLongitude()});

                directBroadcaster.broadcastDirect(msg);
                log.debug("[GPS_PIPELINE] WS_PRED vehicle={} plate={} mode={} frac={} lat={} lon={} speed={}km/h eta_stops={}",
                        state.getVehicleId(), state.getLicensePlate(),
                        fractionValue != null ? "SNAPPED" : "DEAD_RECKONING",
                        fractionValue != null ? String.format("%.4f", fractionValue) : "-",
                        String.format("%.6f", state.getPredictedLatitude()),
                        String.format("%.6f", state.getPredictedLongitude()),
                        String.format("%.1f", state.getSpeedKmh()),
                        nextStops.size());
            } catch (Exception e) {
                log.warn("Failed to broadcast prediction for vehicle {}: {}", state.getVehicleId(), e.getMessage());
            }
        });
    }

    private List<NextStopEta> computeNextStopsEta(VehiclePredictionState state, int maxStops) {
        // Use lastGpsFraction (actual GPS-snapped position) for ETA, not fractionOnRoute
        // (which lags behind due to conservative prediction). This gives accurate distance
        // to upcoming stops based on where the bus ACTUALLY is, not where the marker shows.
        double trueFraction = state.getLastGpsFraction() >= 0
                ? state.getLastGpsFraction()
                : state.getFractionOnRoute();
        if (trueFraction < 0 || state.getTotalRouteDistanceMeters() <= 0
                || state.getRouteNumber() == null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        TimePeriod period = TimePeriod.fromDateTime(now);
        // Use smoothed GPS speed for stable ETA (raw speed can spike 0→50→0)
        double speedKmh = state.getSmoothedSpeedKmh() > 0
                ? state.getSmoothedSpeedKmh()
                : state.getRawGpsSpeedKmh();
        if (speedKmh < etaProperties.getSpeed().getMovingThresholdKmh()) {
            speedKmh = period.getAverageSpeedKmh();
        }
        double effectiveSpeed = speedKmh;
        double totalDist = state.getTotalRouteDistanceMeters();
        double currentFrac = trueFraction;
        double trafficMult = period.getTrafficMultiplier(TimePeriod.isWeekend(now));

        return routeGeometryCache.getStopsAhead(state.getRouteNumber(), state.getDirection(), currentFrac)
                .stream()
                .limit(maxStops)
                .map(stop -> {
                    double stopFrac = stop.getDistanceFromStartMeters() / totalDist;
                    double distMeters = (stopFrac - currentFrac) * totalDist;
                    int etaMin = (int) Math.max(1, Math.ceil(
                            (distMeters / 1000.0 / effectiveSpeed) * 60.0 * trafficMult));
                    return new NextStopEta(stop.getStopId(), stop.getStopName(), etaMin, (int) distMeters);
                })
                .toList();
    }

    private double blendOrAccept(VehiclePredictionState existing, double realLat, double realLon,
                                  boolean isLat) {
        if (existing == null) return isLat ? realLat : realLon;
        double dist = DistanceCalculationService.haversineDistanceMeters(
                existing.getPredictedLatitude(), existing.getPredictedLongitude(), realLat, realLon);
        double cf = properties.getConservativeSpeedFactor();
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

   
    public boolean isActivelyPredicting(String vehicleId) {
        if (!properties.isEnabled()) return false;
        VehiclePredictionState state = vehicleStates.get(vehicleId);
        if (state == null) return false;
        long ageMs = Instant.now().toEpochMilli()
                - (state.getLastReceivedAt() != null ? state.getLastReceivedAt().toEpochMilli() : 0);
        return ageMs <= properties.getMaxAgeMs() && state.isInMotion();
    }

  
    public boolean hasPredictionState(String vehicleId) {
        if (!properties.isEnabled()) return false;
        VehiclePredictionState state = vehicleStates.get(vehicleId);
        if (state == null) return false;
        long ageMs = Instant.now().toEpochMilli()
                - (state.getLastReceivedAt() != null ? state.getLastReceivedAt().toEpochMilli() : 0);
        return ageMs <= 30_000;
    }

    /**
     * Compute confidence level for a vehicle's position data.
     * Used by notification systems to decide whether to trigger push notifications.
     *
     * @return confidence level, or STALE if vehicle not found
     */
    public PositionConfidence getConfidence(String vehicleId) {
        VehiclePredictionState state = vehicleStates.get(vehicleId);
        if (state == null) return PositionConfidence.STALE;
        return computeConfidence(state);
    }

    private PositionConfidence computeConfidence(VehiclePredictionState state) {
        if (state.getLastReceivedAt() == null) return PositionConfidence.STALE;
        long ageMs = Instant.now().toEpochMilli() - state.getLastReceivedAt().toEpochMilli();
        if (ageMs <= 3_000 && state.getFractionOnRoute() >= 0) {
            return PositionConfidence.HIGH;
        }
        if (ageMs <= 10_000) {
            return PositionConfidence.MEDIUM;
        }
        if (ageMs <= 30_000) {
            return PositionConfidence.LOW;
        }
        return PositionConfidence.STALE;
    }

    public int getActiveStateCount() {
        return vehicleStates.size();
    }

    public List<VehiclePredictionState> getActiveStates() {
        if (!properties.isEnabled()) return List.of();
        Instant cutoff = Instant.now().minusMillis(properties.getMaxAgeMs());
        return vehicleStates.values().stream()
                .filter(s -> s.getLastReceivedAt() != null && s.getLastReceivedAt().isAfter(cutoff))
                .filter(s -> s.getFractionOnRoute() >= 0)
                .toList();
    }

    public Map<String, Integer> drainPendingDirectionFixes() {
        if (pendingDirectionFixes.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> result = new java.util.HashMap<>(pendingDirectionFixes);
        pendingDirectionFixes.clear();
        return result;
    }

   
    private static final int SPEED_BUFFER_SIZE = 5;

    /**
     * Append the new speed to the circular buffer, returning a new array.
     * Keeps the last SPEED_BUFFER_SIZE values.
     */
    private double[] appendSpeedToBuffer(VehiclePredictionState existing, double newSpeed) {
        double[] prev = (existing != null) ? existing.getRecentSpeeds() : null;
        if (prev == null || prev.length == 0) {
            return new double[]{newSpeed};
        }
        if (prev.length < SPEED_BUFFER_SIZE) {
            double[] buf = new double[prev.length + 1];
            System.arraycopy(prev, 0, buf, 0, prev.length);
            buf[prev.length] = newSpeed;
            return buf;
        }
        // Shift left, drop oldest
        double[] buf = new double[SPEED_BUFFER_SIZE];
        System.arraycopy(prev, 1, buf, 0, SPEED_BUFFER_SIZE - 1);
        buf[SPEED_BUFFER_SIZE - 1] = newSpeed;
        return buf;
    }

    /**
     * Compute smoothed speed as the average of recent GPS speeds.
     * Eliminates sudden speed spikes (e.g. GPS reports 0→50→0→50)
     * that would make prediction advance jerky.
     */
    private double computeSmoothedSpeed(VehiclePredictionState existing, double newSpeed) {
        double[] buffer = appendSpeedToBuffer(existing, newSpeed);
        double sum = 0;
        for (double s : buffer) sum += s;
        return sum / buffer.length;
    }

    /**
     * Returns the distance in meters to the next stop ahead on the route.
     * Returns -1 if no stop is found or data is unavailable.
     */
    private double computeDistanceToNextStop(VehiclePredictionState state, double totalRouteDistance) {
        if (state.getRouteNumber() == null || totalRouteDistance <= 0) return -1;
        double[] stopFractions = routeGeometryCache.getStopFractions(state.getRouteNumber(), state.getDirection());
        if (stopFractions == null || stopFractions.length == 0) return -1;
        double currentFraction = state.getFractionOnRoute();
        double nextStopFraction = findNextStopFraction(stopFractions, currentFraction, state.getDirection());
        if (nextStopFraction < 0) return -1;
        return Math.abs(nextStopFraction - currentFraction) * totalRouteDistance;
    }

    private double computeStopDecelerationFactor(VehiclePredictionState state, double totalRouteDistance) {
        if (state.getRouteNumber() == null) return 1.0;

        double[] stopFractions = routeGeometryCache.getStopFractions(state.getRouteNumber(), state.getDirection());
        if (stopFractions == null || stopFractions.length == 0) return 1.0;

        double currentFraction = state.getFractionOnRoute();
        double nextStopFraction = findNextStopFraction(stopFractions, currentFraction, state.getDirection());
        if (nextStopFraction < 0) return 1.0;

        double distToStop = Math.abs(nextStopFraction - currentFraction) * totalRouteDistance;
        double zone = properties.getStopDecelerationZoneMeters();
        if (distToStop >= zone) return 1.0;

        double minFactor = properties.getStopDecelerationMinFactor();
        double factor = minFactor + (1.0 - minFactor) * (distToStop / zone);
        log.trace("[STOP_DECAY] vehicle={} route={} distToStop={}m factor={}",
                state.getVehicleId(), state.getRouteNumber(),
                String.format("%.1f", distToStop), String.format("%.2f", factor));
        return factor;
    }

   
    private double findNextStopFraction(double[] sortedFractions, double currentFraction, int direction) {
        // Fraction always increases (both directions), so next stop is always at higher fraction.
        for (double f : sortedFractions) {
            if (f > currentFraction + 0.001) return f;
        }
        return -1;
    }
}
