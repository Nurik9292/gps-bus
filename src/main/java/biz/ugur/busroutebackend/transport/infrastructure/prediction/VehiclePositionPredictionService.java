package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.transport.infrastructure.debug.GpsRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


@Service
@Slf4j
public class VehiclePositionPredictionService {

    private static final double METRES_PER_DEGREE_LAT = 111_320.0;
    private static final double DT_SECONDS = 1.0;
    private static final long MAX_GPS_AGE_MS = 10 * 60 * 1000L;

    private static final double STATIONARY_GPS_THRESHOLD_METERS = 100.0;
    private static final double STALE_PREDICTED_FROM_GPS_METERS = 500.0;
    private static final int TELEPORT_COMMIT_CONFIRMATIONS = 8;
    private static final double TELEPORT_COMMIT_RADIUS_METERS = 150.0;
    private static final long TELEPORT_COMMIT_WINDOW_MS = 120_000;
    private static final double POSITION_JUMP_INTERNAL_THRESHOLD_M = 500.0;

    private static final int OUTLIER_FORCE_ACCEPT_COUNT = 5;
    private static final double OUTLIER_CLUSTER_RADIUS_METERS = 150.0;
    private static final long OUTLIER_FORCE_ACCEPT_WINDOW_MS = 120_000;

    private static final double OFF_ROUTE_DISTANCE_THRESHOLD_METERS = 200.0;
    private static final int OFF_ROUTE_CONFIRMATIONS = 5;

    private record OutlierBaseline(double lat, double lon, int count, Instant firstSeen) {}
    private final ConcurrentHashMap<String, OutlierBaseline> pendingAltBaselines = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, GatekeeperDecision> lastDecisions = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Value("${ugur.diagnostics.tracked-plates:}")
    private String trackedPlatesProperty;
    private volatile java.util.Set<String> trackedPlates = java.util.Set.of();

    @jakarta.annotation.PostConstruct
    public void initTrackedPlates() {
        if (trackedPlatesProperty != null && !trackedPlatesProperty.isBlank()) {
            trackedPlates = java.util.Arrays.stream(trackedPlatesProperty.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            log.info("[DIAG] TRACKED_PLATES enabled: {}", trackedPlates);
        }
    }

    private boolean isTracked(String licensePlate) {
        return licensePlate != null && !trackedPlates.isEmpty() && trackedPlates.contains(licensePlate);
    }

    private VehiclePredictionState replaceState(String vehicleId, VehiclePredictionState newState, String reason) {
        return vehicleStates.compute(vehicleId, (k, existing) -> {
            logStateTransition(vehicleId, existing, newState, reason);
            return newState;
        });
    }

    private VehiclePredictionState updateState(String vehicleId,
                                                java.util.function.Function<VehiclePredictionState, VehiclePredictionState> transform,
                                                String reason) {
        return vehicleStates.compute(vehicleId, (k, existing) -> {
            if (existing == null) return null;
            VehiclePredictionState newState = transform.apply(existing);
            if (newState == null) return existing;
            logStateTransition(vehicleId, existing, newState, reason);
            return newState;
        });
    }

    private void logStateTransition(String vehicleId, VehiclePredictionState existing, VehiclePredictionState newState, String reason) {
        if (existing == null || newState == null) return;
        double prevLat = existing.getPredictedLatitude();
        double prevLon = existing.getPredictedLongitude();
        double newLat = newState.getPredictedLatitude();
        double newLon = newState.getPredictedLongitude();
        if (prevLat != 0.0 && newLat != 0.0) {
            double delta = biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService
                    .haversineDistanceMeters(prevLat, prevLon, newLat, newLon);
            if (delta > POSITION_JUMP_INTERNAL_THRESHOLD_M) {
                log.warn("[GPS_PIPELINE] POSITION_JUMP_INTERNAL vehicle={} plate={} reason={} delta={}m prev=({},{}) new=({},{})",
                        vehicleId, newState.getLicensePlate(), reason,
                        String.format("%.0f", delta),
                        String.format("%.5f", prevLat), String.format("%.5f", prevLon),
                        String.format("%.5f", newLat), String.format("%.5f", newLon));
            }
        }
        if (isTracked(newState.getLicensePlate())) {
            log.info("[GPS_PIPELINE] TRACE_STATE_WRITE vehicle={} plate={} reason={} predicted=({},{}) gps=({},{}) frac={} inMotion={} speed={} coldStartUntil={}",
                    vehicleId, newState.getLicensePlate(), reason,
                    String.format("%.5f", newLat), String.format("%.5f", newLon),
                    String.format("%.5f", newState.getGpsLatitude()),
                    String.format("%.5f", newState.getGpsLongitude()),
                    String.format("%.4f", newState.getFractionOnRoute()),
                    newState.isInMotion(),
                    String.format("%.1f", newState.getSpeedKmh()),
                    newState.getColdStartUntilAt());
        }
    }

    private final ConcurrentHashMap<String, VehiclePredictionState> vehicleStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PendingTeleport> pendingTeleports = new ConcurrentHashMap<>();

    private record PendingTeleport(double lat, double lon, int count, Instant firstSeen) {}

    private final PredictionProperties properties;
    private final PredictionBroadcaster broadcaster;
    private final RouteGeometryCache routeGeometryCache;
    private final VehiclePredictionStateRepository stateRepository;
    private final ObjectProvider<GpsRecorder> gpsRecorderProvider;
    private final GpsOutlierFilter outlierFilter;
    private final SnapCorrector snapCorrector;
    private final VehiclePositionPredictor predictor;

    public VehiclePositionPredictionService(PredictionProperties properties,
                                             PredictionBroadcaster broadcaster,
                                             RouteGeometryCache routeGeometryCache,
                                             VehiclePredictionStateRepository stateRepository,
                                             ObjectProvider<GpsRecorder> gpsRecorderProvider,
                                             GpsOutlierFilter outlierFilter,
                                             SnapCorrector snapCorrector,
                                             VehiclePositionPredictor predictor) {
        this.properties = properties;
        this.broadcaster = broadcaster;
        this.routeGeometryCache = routeGeometryCache;
        this.stateRepository = stateRepository;
        this.gpsRecorderProvider = gpsRecorderProvider;
        this.outlierFilter = outlierFilter;
        this.snapCorrector = snapCorrector;
        this.predictor = predictor;
    }

    private static final Duration RESTORE_TIMEOUT = Duration.ofSeconds(30);

    @EventListener(ApplicationReadyEvent.class)
    public void restoreFromRedis() {
        if (!properties.isEnabled()) return;

        try {
            Mono.when(predictor.loadDwellStats(), loadPredictionStates())
                    .subscribeOn(Schedulers.boundedElastic())
                    .block(RESTORE_TIMEOUT);
        } catch (RuntimeException e) {
            log.warn("Prediction state restore aborted after {}s, continuing with empty cache: {}",
                    RESTORE_TIMEOUT.toSeconds(), e.getMessage());
        }
    }

    private Mono<Void> loadPredictionStates() {
        return stateRepository.loadAll()
                .filter(state -> state.getVehicleId() != null)
                .map(this::attachRouteGeometry)
                .map(state -> state.toBuilder()
                        .lastReceivedAt(Instant.now().minusSeconds(properties.getMaxAgeMs() / 1000 + 60))
                        .build())
                .doOnNext(state -> {
                    replaceState(state.getVehicleId(), state, "redis-restore");
                    log.debug("Restored prediction state (stale, awaiting fresh GPS): vehicle={} route={} frac={}",
                            state.getVehicleId(), state.getRouteNumber(),
                            state.getFractionOnRoute() >= 0
                                    ? String.format("%.4f", state.getFractionOnRoute()) : "-");
                })
                .doOnError(err -> log.warn("Error restoring prediction states: {}", err.getMessage()))
                .onErrorResume(err -> Flux.empty())
                .then(Mono.fromRunnable(() ->
                        log.info("Prediction states restored from Redis: {} vehicles (awaiting fresh GPS before broadcast)",
                                vehicleStates.size())));
    }

    private VehiclePredictionState attachRouteGeometry(VehiclePredictionState state) {
        if (state.getRouteNumber() == null) {
            return state;
        }
        List<double[]> coords = routeGeometryCache.getPoints(state.getRouteNumber(), state.getDirection());
        if (coords == null) {
            return state;
        }
        double totalDist = routeGeometryCache.getTotalDistance(state.getRouteNumber(), state.getDirection());
        return state.toBuilder()
                .routeCoordinates(coords)
                .totalRouteDistanceMeters(totalDist)
                .build();
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
                            int direction,
                            boolean inGarage) {
        if (!properties.isEnabled()) {
            return;
        }
        if (inGarage) {
            VehiclePredictionState existingGarage = vehicleStates.get(vehicleId);
            if (existingGarage != null) {
                replaceState(vehicleId,
                        existingGarage.toBuilder()
                                .inGarage(true)
                                .lastReceivedAt(Instant.now())
                                .build(),
                        "garage-enter");
            }
            pendingTeleports.remove(vehicleId);
            return;
        }

        GpsRecorder recorder = gpsRecorderProvider.getIfAvailable();
        if (recorder != null) {
            recorder.recordIfActive(vehicleId, licensePlate, routeNumber,
                    latitude, longitude, speedKmh, course, inMotion, timestamp, direction);
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

        GpsOutlierFilter.Decision outlierDecision = outlierFilter.evaluate(
                existing, latitude, longitude, timestamp, vehicleId, licensePlate);
        boolean forceAcceptAfterOutlier = false;
        switch (outlierDecision) {
            case REJECT_HARD_OUTLIER, REJECT_SOFT_OUTLIER -> {
                if (shouldForceAcceptStaleBaseline(vehicleId, licensePlate, latitude, longitude)) {
                    forceAcceptAfterOutlier = true;
                    pendingAltBaselines.remove(vehicleId);
                    lastDecisions.put(vehicleId, GatekeeperDecision.FORCE_ACCEPT_STALE);
                } else {
                    replaceState(vehicleId, existing.toBuilder()
                            .lastReceivedAt(Instant.now())
                            .build(), "outlier-reject");
                    lastDecisions.put(vehicleId, GatekeeperDecision.REJECT_OUTLIER);
                    return;
                }
            }
            case REJECT_TELEPORT_GAP -> {
                replaceState(vehicleId, existing.toBuilder()
                        .lastGpsUpdate(timestamp)
                        .lastReceivedAt(Instant.now())
                        .build(), "teleport-gap-reject");
                lastDecisions.put(vehicleId, GatekeeperDecision.REJECT_TELEPORT_GAP);
                return;
            }
            case ACCEPT -> pendingAltBaselines.remove(vehicleId);
        }

        if (forceAcceptAfterOutlier && existing != null) {
            existing = existing.toBuilder()
                    .gpsLatitude(latitude)
                    .gpsLongitude(longitude)
                    .predictedLatitude(latitude)
                    .predictedLongitude(longitude)
                    .fractionOnRoute(-1)
                    .lastRejectedGpsFraction(-1)
                    .consecutiveImplausibleCount(0)
                    .consecutiveInconsistentAdvanceCount(0)
                    .build();
            pendingTeleports.remove(vehicleId);
        }

        SnapCorrector.SnapResult snapResult = snapCorrector.applySnap(
                existing, vehicleId, licensePlate, routeNumber,
                latitude, longitude, course, direction);
        double predictedLat = snapResult.predictedLatitude();
        double predictedLon = snapResult.predictedLongitude();
        double fraction = snapResult.fraction();
        direction = snapResult.direction();
        List<double[]> routeCoords = snapResult.routeCoords();
        double totalDist = snapResult.totalRouteDistanceMeters();
        course = snapResult.course();
        double newRejectedFrac = snapResult.newRejectedFrac();
        int newImplausibleCount = snapResult.newImplausibleCount();


        boolean positionTeleport = false;
        boolean rawGpsStationary = false;
        boolean snapDriftRelativeToGps = false;
        if (existing != null
                && existing.getPredictedLatitude() != 0.0
                && existing.getPredictedLongitude() != 0.0) {
            double distFromPredicted = DistanceCalculationService.haversineDistanceMeters(
                    existing.getPredictedLatitude(), existing.getPredictedLongitude(),
                    predictedLat, predictedLon);
            double distFromLastGps = DistanceCalculationService.haversineDistanceMeters(
                    existing.getGpsLatitude(), existing.getGpsLongitude(),
                    latitude, longitude);
            rawGpsStationary = distFromLastGps <= STATIONARY_GPS_THRESHOLD_METERS;
            boolean snapJumpDisproportionate =
                    distFromLastGps > 0 && distFromPredicted / distFromLastGps > 5.0;
            snapDriftRelativeToGps = rawGpsStationary || snapJumpDisproportionate;
            if (distFromPredicted > properties.getTeleportThresholdMeters()) {
                log.info("[GPS_PIPELINE] SNAP_TELEPORT vehicle={} plate={} dist={}m rawGpsMove={}m kind={} — pending confirmation",
                        vehicleId, licensePlate,
                        String.format("%.0f", distFromPredicted),
                        String.format("%.0f", distFromLastGps),
                        snapDriftRelativeToGps ? "snap-drift" : "raw-gps-jump");
                positionTeleport = true;
            }
        }

        boolean teleportRejected = false;
        if (positionTeleport && existing != null) {
            if (snapDriftRelativeToGps) {
                teleportRejected = true;
                pendingTeleports.remove(vehicleId);
            } else {
                Instant now = Instant.now();
                PendingTeleport pending = pendingTeleports.get(vehicleId);
                boolean pendingExpired = pending == null
                        || now.toEpochMilli() - pending.firstSeen().toEpochMilli() > TELEPORT_COMMIT_WINDOW_MS;
                boolean matchesPending = !pendingExpired
                        && DistanceCalculationService.haversineDistanceMeters(
                                pending.lat(), pending.lon(), predictedLat, predictedLon)
                           <= TELEPORT_COMMIT_RADIUS_METERS;

                if (matchesPending) {
                    int newCount = pending.count() + 1;
                    if (newCount >= TELEPORT_COMMIT_CONFIRMATIONS) {
                        pendingTeleports.remove(vehicleId);
                        log.info("[GPS_PIPELINE] TELEPORT_CONFIRMED vehicle={} plate={} count={} — committing new position",
                                vehicleId, licensePlate, newCount);
                    } else {
                        pendingTeleports.put(vehicleId,
                                new PendingTeleport(predictedLat, predictedLon, newCount, pending.firstSeen()));
                        teleportRejected = true;
                        log.info("[GPS_PIPELINE] TELEPORT_PENDING vehicle={} plate={} count={}/{} — keeping previous position",
                                vehicleId, licensePlate, newCount, TELEPORT_COMMIT_CONFIRMATIONS);
                    }
                } else {
                    pendingTeleports.put(vehicleId,
                            new PendingTeleport(predictedLat, predictedLon, 1, now));
                    teleportRejected = true;
                    log.info("[GPS_PIPELINE] TELEPORT_PENDING_NEW vehicle={} plate={} count=1/{} at=({},{}) — keeping previous position",
                            vehicleId, licensePlate, TELEPORT_COMMIT_CONFIRMATIONS,
                            String.format("%.5f", predictedLat), String.format("%.5f", predictedLon));
                }
            }
        } else if (!positionTeleport) {
            pendingTeleports.remove(vehicleId);
        }

        if (teleportRejected) {
            predictedLat = existing.getPredictedLatitude();
            predictedLon = existing.getPredictedLongitude();
            fraction = existing.getFractionOnRoute();
            routeCoords = existing.getRouteCoordinates();
            totalDist = existing.getTotalRouteDistanceMeters();
            course = existing.getCourse();
            direction = existing.getDirection();
            if (!rawGpsStationary) {
                latitude = existing.getGpsLatitude();
                longitude = existing.getGpsLongitude();
            }
            positionTeleport = false;
        }

        boolean routeChanged = existing != null
                && existing.getRouteNumber() != null
                && !existing.getRouteNumber().equals(routeNumber);
        if (routeChanged) {
            log.info("[GPS_PIPELINE] ROUTE_CHANGED vehicle={} plate={} from={} to={} — resetting off-route state",
                    vehicleId, licensePlate, existing.getRouteNumber(), routeNumber);
        }
        int newOffRouteCount = (existing != null && !routeChanged) ? existing.getConsecutiveOffRouteCount() : 0;
        boolean newOffRoute = existing != null && !routeChanged && existing.isOffRoute();
        if (fraction >= 0 && !teleportRejected) {
            double rawToSnapDist = DistanceCalculationService.haversineDistanceMeters(
                    latitude, longitude, predictedLat, predictedLon);
            if (rawToSnapDist > OFF_ROUTE_DISTANCE_THRESHOLD_METERS) {
                newOffRouteCount += 1;
                if (newOffRouteCount >= OFF_ROUTE_CONFIRMATIONS && !newOffRoute) {
                    newOffRoute = true;
                    log.warn("[GPS_PIPELINE] OFF_ROUTE_DETECTED vehicle={} plate={} route={} consecutive={} rawToSnapDist={}m — suppressing broadcast until back on route",
                            vehicleId, licensePlate, routeNumber, newOffRouteCount,
                            String.format("%.0f", rawToSnapDist));
                }
            } else {
                if (newOffRoute) {
                    log.info("[GPS_PIPELINE] OFF_ROUTE_CLEARED vehicle={} plate={} route={} — vehicle returned within {}m of route",
                            vehicleId, licensePlate, routeNumber, (int) OFF_ROUTE_DISTANCE_THRESHOLD_METERS);
                }
                newOffRouteCount = 0;
                newOffRoute = false;
            }
        }

        VehiclePredictionState.VehiclePredictionStateBuilder builder = VehiclePredictionState.builder()
                .vehicleId(vehicleId)
                .licensePlate(licensePlate)
                .routeNumber(routeNumber)
                .gpsLatitude(latitude)
                .gpsLongitude(longitude)
                .speedKmh(PredictionMath.computeSmoothedSpeed(existing != null ? existing.getRecentSpeeds() : null, speedKmh))
                .rawGpsSpeedKmh(speedKmh)
                .smoothedSpeedKmh(PredictionMath.computeSmoothedSpeed(existing != null ? existing.getRecentSpeeds() : null, speedKmh))
                .recentSpeeds(PredictionMath.appendSpeedToBuffer(existing != null ? existing.getRecentSpeeds() : null, speedKmh))
                .course(course)
                .inMotion(inMotion)
                .lastGpsUpdate(timestamp)
                .predictedLatitude(predictedLat)
                .predictedLongitude(predictedLon)
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
                .consecutiveOffRouteCount(newOffRouteCount)
                .offRoute(newOffRoute)
                .inGarage(false)
                .direction(direction);

        boolean triggerColdStart = snapResult.resetTriggered() || positionTeleport;
        Instant coldStartUntilAt = triggerColdStart
                ? Instant.now().plusSeconds(properties.getColdStartDurationSec())
                : (existing != null ? existing.getColdStartUntilAt() : null);

        VehiclePredictionState builtState = builder
                .lastReceivedAt(Instant.now())
                .coldStartUntilAt(coldStartUntilAt)
                .build();
        String writeReason = teleportRejected ? "onGpsUpdate-teleport-rejected"
                : (triggerColdStart ? (snapResult.resetTriggered() ? "onGpsUpdate-snap-reset" : "onGpsUpdate-pos-teleport")
                        : "onGpsUpdate-accept");
        replaceState(vehicleId, builtState, writeReason);
        if (teleportRejected) {
            lastDecisions.put(vehicleId, GatekeeperDecision.PENDING_TELEPORT);
        } else if (triggerColdStart) {
            lastDecisions.put(vehicleId, GatekeeperDecision.COLD_START);
        } else if (!forceAcceptAfterOutlier) {
            lastDecisions.put(vehicleId, GatekeeperDecision.ACCEPT);
        }
        if (triggerColdStart) {
            log.warn("[GPS_PIPELINE] COLD_START vehicle={} plate={} route={} reason={} duration={}s — WS broadcast suppressed until state stabilizes",
                    vehicleId, licensePlate, routeNumber,
                    snapResult.resetTriggered() ? "snap-implausible" : "position-teleport",
                    properties.getColdStartDurationSec());
        }
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

        vehicleStates.keySet().forEach(vid -> {
            updateState(vid, current -> {
                if (isAtRouteBoundary(current)
                        && current.isInMotion()
                        && current.getSpeedKmh() >= minSpeed) {
                    return current.toBuilder().fractionOnRoute(-1).build();
                }
                return null;
            }, "route-boundary-reset");
        });

        List<VehiclePredictionState> movingStates = vehicleStates.values().stream()
                .filter(state -> state.isInMotion() && state.getSpeedKmh() >= minSpeed)
                .filter(state -> state.getLastReceivedAt() != null
                        && (now.toEpochMilli() - state.getLastReceivedAt().toEpochMilli()) <= maxAgeMs)
                .filter(state -> state.getRouteCoordinates() != null
                        ? state.getFractionOnRoute() >= 0
                        : true)
                .toList();

        List<VehiclePredictionState> stoppedStates = vehicleStates.values().stream()
                .filter(state -> !state.isInMotion() || state.getSpeedKmh() < minSpeed)
                .filter(state -> state.getLastReceivedAt() != null
                        && (now.toEpochMilli() - state.getLastReceivedAt().toEpochMilli()) <= maxAgeMs)
                .filter(state -> {
                    Instant lastBroadcast = state.getLastBroadcastAt();
                    double[] prev = broadcaster.getLastBroadcastPosition(state.getVehicleId());
                    if (prev != null) {
                        double moved = DistanceCalculationService.haversineDistanceMeters(
                                prev[0], prev[1],
                                state.getPredictedLatitude(), state.getPredictedLongitude());
                        if (moved > 5.0) return true;
                    }
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
                .flatMap(snapshot -> {
                    VehiclePredictionState advanced = updateState(snapshot.getVehicleId(),
                            current -> predictor.advance(current)
                                    .toBuilder().lastBroadcastAt(now).build(),
                            "predictor-advance");
                    if (advanced == null) return Mono.empty();
                    return broadcaster.broadcast(advanced);
                })
                .then();

        Mono<Void> stoppedMono = Flux.fromIterable(stoppedStates)
                .flatMap(snapshot -> {
                    VehiclePredictionState marked = updateState(snapshot.getVehicleId(),
                            current -> current.toBuilder().lastBroadcastAt(now).build(),
                            "stopped-mark-broadcast");
                    if (marked == null) return Mono.empty();
                    return broadcaster.broadcast(marked);
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
                String vehicleId = e.getKey();
                stateRepository.delete(vehicleId)
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe(null, err -> log.warn(
                                "Redis state delete failed for stale vehicle {}: {}",
                                vehicleId, err.getMessage()));
            }
            return stale;
        });
        pendingTeleports.keySet().retainAll(vehicleStates.keySet());
        pendingAltBaselines.keySet().retainAll(vehicleStates.keySet());
        lastDecisions.keySet().retainAll(vehicleStates.keySet());
        snapCorrector.onVehicleStaleCleanup(vehicleStates.keySet());
        broadcaster.onVehiclesStaleCleanup(vehicleStates.keySet());
    }

    private boolean shouldForceAcceptStaleBaseline(String vehicleId, String licensePlate,
                                                    double latitude, double longitude) {
        Instant now = Instant.now();
        OutlierBaseline current = pendingAltBaselines.get(vehicleId);
        if (current == null || now.toEpochMilli() - current.firstSeen().toEpochMilli() > OUTLIER_FORCE_ACCEPT_WINDOW_MS) {
            pendingAltBaselines.put(vehicleId,
                    new OutlierBaseline(latitude, longitude, 1, now));
            return false;
        }
        double distFromCluster = DistanceCalculationService.haversineDistanceMeters(
                current.lat(), current.lon(), latitude, longitude);
        if (distFromCluster > OUTLIER_CLUSTER_RADIUS_METERS) {
            pendingAltBaselines.put(vehicleId,
                    new OutlierBaseline(latitude, longitude, 1, now));
            return false;
        }
        int newCount = current.count() + 1;
        if (newCount >= OUTLIER_FORCE_ACCEPT_COUNT) {
            log.warn("[GPS_PIPELINE] OUTLIER_FORCE_ACCEPT vehicle={} plate={} count={} cluster=({},{}) — baseline stale, resetting state",
                    vehicleId, licensePlate, newCount,
                    String.format("%.5f", latitude), String.format("%.5f", longitude));
            return true;
        }
        pendingAltBaselines.put(vehicleId,
                new OutlierBaseline(current.lat(), current.lon(), newCount, current.firstSeen()));
        return false;
    }

    private boolean isAtRouteBoundary(VehiclePredictionState state) {
        if (state.getRouteCoordinates() == null || state.getFractionOnRoute() < 0) {
            return false;
        }
        return state.getFractionOnRoute() >= 1.0;
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

   
    public PositionConfidence getConfidence(String vehicleId) {
        VehiclePredictionState state = vehicleStates.get(vehicleId);
        if (state == null) return PositionConfidence.STALE;
        return PredictionMath.computeConfidence(state.getLastReceivedAt(), state.getFractionOnRoute(), Instant.now());
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

    List<VehiclePredictionState> snapshotAllStatesForTest() {
        return List.copyOf(vehicleStates.values());
    }

    public boolean isInColdStart(String vehicleId) {
        VehiclePredictionState state = vehicleStates.get(vehicleId);
        return state != null && PredictionBroadcaster.isInColdStart(state);
    }

    public boolean hasPendingTeleport(String vehicleId) {
        return pendingTeleports.containsKey(vehicleId);
    }

    public GatekeeperDecision evaluateGate(String vehicleId) {
        VehiclePredictionState state = vehicleStates.get(vehicleId);
        if (state == null) {
            return GatekeeperDecision.ACCEPT;
        }
        if (PredictionBroadcaster.isInColdStart(state)) {
            return GatekeeperDecision.COLD_START;
        }
        if (pendingTeleports.containsKey(vehicleId)) {
            return GatekeeperDecision.PENDING_TELEPORT;
        }
        if (state.isOffRoute()) {
            return GatekeeperDecision.REJECT_OFF_ROUTE;
        }
        return lastDecisions.getOrDefault(vehicleId, GatekeeperDecision.ACCEPT);
    }

    public double[] getAcceptedPosition(String vehicleId) {
        VehiclePredictionState state = vehicleStates.get(vehicleId);
        if (state == null || state.getPredictedLatitude() == 0.0) return null;
        return new double[]{state.getPredictedLatitude(), state.getPredictedLongitude()};
    }

    public Map<String, Integer> drainPendingDirectionFixes() {
        return snapCorrector.drainPendingDirectionFixes();
    }


}
