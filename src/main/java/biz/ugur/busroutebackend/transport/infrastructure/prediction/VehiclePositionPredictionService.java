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
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        state -> {
                            vehicleStates.put(state.getVehicleId(), state);
                            log.debug("Restored prediction state: vehicle={} route={} frac={}",
                                    state.getVehicleId(), state.getRouteNumber(),
                                    state.getFractionOnRoute() >= 0
                                            ? String.format("%.4f", state.getFractionOnRoute()) : "-");
                        },
                        err -> log.warn("Error restoring prediction states: {}", err.getMessage()),
                        () -> log.info("Prediction states restored from Redis: {} vehicles", vehicleStates.size())
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
                                fracCorrected = true;
                            }
                        }
                    } else if (gpsMoveAgainstDir) {
                        log.debug("[GPS_PIPELINE] DIR_CORRECT_FRAC_SKIP vehicle={} route={} dir={} delta={} (jump too large or heading corrected)",
                                vehicleId, routeNumber, direction, String.format("%.4f", fracDelta));
                    }
                }

                boolean plausibleSnap = true;
                boolean overrideActive = false;

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
                            // Vehicle has consistently reported this location — accept it as ground truth.
                            // overrideActive bypasses the realIsAhead check so we always use real GPS position.
                            log.info("[GPS_PIPELINE] SNAP_IMPLAUSIBLE_OVERRIDE vehicle={} route={} dir={} frac={}→{} jump={} ({}x consistent GPS)",
                                    vehicleId, routeNumber, direction,
                                    String.format("%.4f", existing.getLastGpsFraction()),
                                    String.format("%.4f", realFraction),
                                    String.format("%.4f", jumpSize),
                                    newImplausibleCount);
                            newImplausibleCount = 0;
                            newRejectedFrac = -1;
                            overrideActive = true;
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

                // overrideActive: GPS force-accepted regardless of ahead/behind check.
                // Fraction always increases (both forward and backward geometries are traversed 0→1).
                boolean realIsAhead = overrideActive || (plausibleSnap && (
                        predictedFraction < 0 || realFraction >= predictedFraction));

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
        double decayedSpeedKmh = state.getSpeedKmh() * properties.getDecayFactor();

        List<double[]> routeCoords = state.getRouteCoordinates();
        double totalRouteDistance = state.getTotalRouteDistanceMeters();

        if (routeCoords != null && state.getFractionOnRoute() >= 0 && totalRouteDistance > 0) {
            double stopFactor = computeStopDecelerationFactor(state, totalRouteDistance);
            double speedMs = (decayedSpeedKmh * stopFactor) / 3.6;
            double fractionDelta = speedMs * DT_SECONDS / totalRouteDistance;

            // Both forward and backward geometries are stored start→end in natural traversal order.
            // Fraction always increases toward 1.0 regardless of direction.
            double newFraction = Math.min(state.getFractionOnRoute() + fractionDelta, 1.0);

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
                fractionValue
        );

        return Mono.fromRunnable(() -> {
            try {
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
        if (state.getFractionOnRoute() < 0 || state.getTotalRouteDistanceMeters() <= 0
                || state.getRouteNumber() == null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        TimePeriod period = TimePeriod.fromDateTime(now);
        double speedKmh = state.getSpeedKmh();
        if (speedKmh < etaProperties.getSpeed().getMovingThresholdKmh()) {
            speedKmh = period.getAverageSpeedKmh();
        }
        double effectiveSpeed = speedKmh;
        double totalDist = state.getTotalRouteDistanceMeters();
        double currentFrac = state.getFractionOnRoute();
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

    /**
     * Returns true only when prediction is actively broadcasting for this vehicle (moving + recent GPS).
     * Stopped vehicles are not handled by prediction, so raw GPS force-publish should be allowed for them.
     */
    public boolean isActivelyPredicting(String vehicleId) {
        if (!properties.isEnabled()) return false;
        VehiclePredictionState state = vehicleStates.get(vehicleId);
        if (state == null) return false;
        long ageMs = Instant.now().toEpochMilli()
                - (state.getLastReceivedAt() != null ? state.getLastReceivedAt().toEpochMilli() : 0);
        return ageMs <= properties.getMaxAgeMs() && state.isInMotion();
    }

    public int getActiveStateCount() {
        return vehicleStates.size();
    }

    /**
     * Returns a snapshot of all current prediction states for ETA calculations.
     * Only includes states with a valid fractionOnRoute and within maxAgeMs.
     */
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

    /**
     * Вычисляет коэффициент замедления (0.0–1.0) при приближении к следующей остановке.
     * Если ТС находится в зоне замедления (< stopDecelerationZoneMeters от остановки),
     * скорость плавно снижается до stopDecelerationMinFactor×speedKmh.
     */
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

    /**
     * Находит ближайшую следующую остановку в направлении движения ТС.
     * Для direction=0 (вперёд): ищем минимальную фракцию > currentFraction.
     * Для direction=1 (назад):  ищем максимальную фракцию < currentFraction.
     * @return фракция остановки, или -1 если не найдена
     */
    private double findNextStopFraction(double[] sortedFractions, double currentFraction, int direction) {
        // Fraction always increases (both directions), so next stop is always at higher fraction.
        for (double f : sortedFractions) {
            if (f > currentFraction + 0.001) return f;
        }
        return -1;
    }
}
