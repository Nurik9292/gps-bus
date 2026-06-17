package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.infrastructure.debug.GpsRecorder;
import biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer;
import biz.ugur.busroutebackend.transport.infrastructure.monitoring.offroute.VehicleOffRouteAlertMonitor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


@Service
@Slf4j
public class VehiclePositionPredictionService {

    private static final double METRES_PER_DEGREE_LAT = 111_320.0;
    private static final double DT_SECONDS = 1.0;
    private static final long MAX_GPS_AGE_MS = 10 * 60 * 1000L;




    private record OutlierBaseline(double lat, double lon, int count, Instant firstSeen) {}
    private final ConcurrentHashMap<String, OutlierBaseline> pendingAltBaselines = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, GatekeeperDecision> lastDecisions = new ConcurrentHashMap<>();

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
        if (newState == null) return;
        double newLat = newState.getPredictedLatitude();
        double newLon = newState.getPredictedLongitude();
        if (existing != null) {
            double prevLat = existing.getPredictedLatitude();
            double prevLon = existing.getPredictedLongitude();
            if (prevLat != 0.0 && newLat != 0.0) {
                double delta = DistanceCalculationService.haversineDistanceMeters(prevLat, prevLon, newLat, newLon);
                if (delta > properties.getPositionJumpInternalThresholdMeters()) {
                    log.warn("[GPS_PIPELINE] POSITION_JUMP_INTERNAL vehicle={} plate={} reason={} delta={}m prev=({},{}) new=({},{})",
                            vehicleId, newState.getLicensePlate(), reason,
                            String.format("%.0f", delta),
                            String.format("%.5f", prevLat), String.format("%.5f", prevLon),
                            String.format("%.5f", newLat), String.format("%.5f", newLon));
                }
            }
        }
        pipelineTracer.traceStateWrite(
                vehicleId, newState.getLicensePlate(), newState.getRouteNumber(), reason,
                newState.getPredictedLatitude(), newState.getPredictedLongitude(),
                newState.getGpsLatitude(), newState.getGpsLongitude(),
                newState.getFractionOnRoute(), newState.isInMotion(), newState.getSpeedKmh(),
                newState.getDirection(), newState.isDirectionConfirmed(),
                newState.getColdStartUntilAt());
    }

    private final ConcurrentHashMap<String, VehiclePredictionState> vehicleStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PendingTeleport> pendingTeleports = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> pendingDirectionChanges = new ConcurrentHashMap<>();

    private record PendingTeleport(double lat, double lon, double fraction, int direction,
                                   int count, Instant firstSeen) {}

    private final PredictionProperties properties;
    private final PredictionBroadcaster broadcaster;
    private final RouteGeometryCache routeGeometryCache;
    private final VehiclePredictionStateRepository stateRepository;
    private final ObjectProvider<GpsRecorder> gpsRecorderProvider;
    private final GpsOutlierFilter outlierFilter;
    private final SnapCorrector snapCorrector;
    private final VehiclePositionPredictor predictor;
    private final Optional<VehicleOffRouteAlertMonitor> offRouteMonitor;
    private final Clock clock;
    private final PipelineTracer pipelineTracer;

    public VehiclePositionPredictionService(PredictionProperties properties,
                                             PredictionBroadcaster broadcaster,
                                             RouteGeometryCache routeGeometryCache,
                                             VehiclePredictionStateRepository stateRepository,
                                             ObjectProvider<GpsRecorder> gpsRecorderProvider,
                                             GpsOutlierFilter outlierFilter,
                                             SnapCorrector snapCorrector,
                                             VehiclePositionPredictor predictor,
                                             Optional<VehicleOffRouteAlertMonitor> offRouteMonitor,
                                             Clock clock,
                                             PipelineTracer pipelineTracer) {
        this.properties = properties;
        this.broadcaster = broadcaster;
        this.routeGeometryCache = routeGeometryCache;
        this.stateRepository = stateRepository;
        this.gpsRecorderProvider = gpsRecorderProvider;
        this.outlierFilter = outlierFilter;
        this.snapCorrector = snapCorrector;
        this.predictor = predictor;
        this.offRouteMonitor = offRouteMonitor;
        this.clock = clock;
        this.pipelineTracer = pipelineTracer;
    }

    private static final Duration RESTORE_TIMEOUT = Duration.ofSeconds(30);

    @EventListener(ApplicationReadyEvent.class)
    public void restoreFromRedis() {
        if (!properties.isEnabled()) return;

        Mono.when(predictor.loadDwellStats(), predictor.loadSegmentTravelStats(), loadPredictionStates())
                .timeout(RESTORE_TIMEOUT)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        err -> log.warn(
                                "[GPS_PIPELINE] Prediction state restore aborted after {}s, continuing with empty cache: {}",
                                RESTORE_TIMEOUT.toSeconds(), err.getMessage()),
                        () -> log.info(
                                "[GPS_PIPELINE] Prediction state restored from Redis: {} vehicles",
                                vehicleStates.size()));
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
                .doOnError(err -> log.warn("[GPS_PIPELINE] Error restoring prediction states: {}", err.getMessage()))
                .onErrorResume(err -> Flux.empty())
                .then(Mono.fromRunnable(() ->
                        log.info("[GPS_PIPELINE] Prediction states restored from Redis: {} vehicles (awaiting fresh GPS before broadcast)",
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
                            boolean directionConfirmed,
                            boolean inGarage) {
        onGpsUpdate(vehicleId, licensePlate, routeNumber, latitude, longitude, speedKmh,
                course, inMotion, timestamp, direction, directionConfirmed, inGarage, false);
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
                            boolean directionConfirmed,
                            boolean inGarage,
                            boolean isBuffered) {
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

        if (isBuffered) {
            VehiclePredictionState existingBuffered = vehicleStates.get(vehicleId);
            if (existingBuffered != null) {
                replaceState(vehicleId,
                        existingBuffered.toBuilder()
                                .gpsLatitude(latitude)
                                .gpsLongitude(longitude)
                                .lastGpsUpdate(timestamp)
                                .lastReceivedAt(Instant.now())
                                .build(),
                        "buffered-gps-baseline-only");
                log.debug("[GPS_PIPELINE] BUFFERED_GPS vehicle={} plate={} — baseline updated, prediction advance skipped",
                        vehicleId, licensePlate);
            }
            return;
        }

        VehiclePredictionState existing = vehicleStates.get(vehicleId);

        if (existing != null && existing.getDirection() != direction) {
            int confirmations = pendingDirectionChanges.merge(vehicleId, 1, Integer::sum);
            if (confirmations < properties.getDirectionChangeConfirmations()) {
                log.debug("[GPS_PIPELINE] DIR_EXTERNAL_CHANGE_PENDING vehicle={} plate={} candidate={} confirmations={}/{} — holding direction {}",
                        vehicleId, licensePlate, direction, confirmations,
                        properties.getDirectionChangeConfirmations(), existing.getDirection());
                direction = existing.getDirection();
            } else {
                pendingDirectionChanges.remove(vehicleId);
                log.info("[GPS_PIPELINE] DIR_EXTERNAL_CHANGE vehicle={} plate={} prevDir={} newDir={} confirmations={} — full reset of direction-tied state, cooldown started",
                        vehicleId, licensePlate, existing.getDirection(), direction, confirmations);
                double prevPredictedLat = existing.getPredictedLatitude();
                double prevPredictedLon = existing.getPredictedLongitude();
                double flipPositionJumpMeters = (prevPredictedLat != 0.0 && prevPredictedLon != 0.0)
                        ? DistanceCalculationService.haversineDistanceMeters(prevPredictedLat, prevPredictedLon, latitude, longitude)
                        : 0.0;
                boolean holdPositionThroughTeleportGate =
                        flipPositionJumpMeters > properties.getPositionJumpInternalThresholdMeters();
                existing = existing.toBuilder()
                        .direction(direction)
                        .directionConfirmed(directionConfirmed || existing.isDirectionConfirmed())
                        .fractionOnRoute(-1)
                        .lastGpsFraction(-1)
                        .lastRejectedGpsFraction(-1)
                        .consecutiveImplausibleCount(0)
                        .consecutiveInconsistentAdvanceCount(0)
                        .consecutiveOffRouteCount(0)
                        .offRoute(false)
                        .predictedLatitude(holdPositionThroughTeleportGate ? prevPredictedLat : latitude)
                        .predictedLongitude(holdPositionThroughTeleportGate ? prevPredictedLon : longitude)
                        .routeCoordinates(null)
                        .totalRouteDistanceMeters(0)
                        .dwellStartedAt(null)
                        .dwellStopFraction(-1)
                        .dwellStopId(null)
                        .directionChangedAt(Instant.now())
                        .build();
                if (holdPositionThroughTeleportGate) {
                    log.info("[GPS_PIPELINE] DIR_FLIP_POSITION_HELD vehicle={} plate={} jump={}m — keeping previous position through teleport gate until new-direction snap is confirmed",
                            vehicleId, licensePlate, String.format("%.0f", flipPositionJumpMeters));
                }
                replaceState(vehicleId, existing, "direction-external-change");
            }
        } else {
            pendingDirectionChanges.remove(vehicleId);
        }

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
                    lastDecisions.put(vehicleId, GatekeeperDecision.REJECT_OUTLIER);
                    return;
                }
            }
            case REJECT_TELEPORT_GAP -> {
                replaceState(vehicleId, existing.toBuilder()
                        .lastGpsUpdate(timestamp)
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
            rawGpsStationary = distFromLastGps <= properties.getStationaryGpsThresholdMeters();
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
                boolean pendingActive = pending != null
                        && now.toEpochMilli() - pending.firstSeen().toEpochMilli() <= properties.getTeleportCommitWindowMs();

                if (pendingActive) {
                    double distToPending = DistanceCalculationService.haversineDistanceMeters(
                            pending.lat(), pending.lon(), predictedLat, predictedLon);
                    long pendingAgeMs = now.toEpochMilli() - pending.firstSeen().toEpochMilli();
                    boolean trajectoryConsistent = isTrajectoryAdvance(
                            pending, fraction, direction, predictedLat, predictedLon);
                    int newCount = pending.count() + 1;

                    if (trajectoryConsistent) {
                        boolean fastConfirm = newCount >= properties.getTeleportCommitConfirmationsTrajectory()
                                || pendingAgeMs > properties.getTeleportFastConfirmAfterMs();
                        if (fastConfirm) {
                            pendingTeleports.remove(vehicleId);
                            log.info("[GPS_PIPELINE] TELEPORT_CONFIRMED_TRAJECTORY vehicle={} plate={} count={} ageMs={} fracDelta={} — route-consistent advance, accepting",
                                    vehicleId, licensePlate, newCount, pendingAgeMs,
                                    String.format("%.4f", fraction - pending.fraction()));
                        } else {
                            pendingTeleports.put(vehicleId,
                                    new PendingTeleport(predictedLat, predictedLon, fraction, direction,
                                            newCount, pending.firstSeen()));
                            teleportRejected = true;
                            log.info("[GPS_PIPELINE] TELEPORT_PENDING_TRAJECTORY vehicle={} plate={} count={}/{} ageMs={} — advancing pending center forward",
                                    vehicleId, licensePlate, newCount, properties.getTeleportCommitConfirmationsTrajectory(), pendingAgeMs);
                        }
                    } else if (distToPending <= properties.getTeleportCommitRadiusMeters()) {
                        if (newCount >= properties.getTeleportCommitConfirmations()) {
                            pendingTeleports.remove(vehicleId);
                            log.info("[GPS_PIPELINE] TELEPORT_CONFIRMED vehicle={} plate={} count={} — committing new position",
                                    vehicleId, licensePlate, newCount);
                        } else {
                            pendingTeleports.put(vehicleId,
                                    new PendingTeleport(predictedLat, predictedLon, fraction, direction,
                                            newCount, pending.firstSeen()));
                            teleportRejected = true;
                            log.info("[GPS_PIPELINE] TELEPORT_PENDING vehicle={} plate={} count={}/{} — keeping previous position",
                                    vehicleId, licensePlate, newCount, properties.getTeleportCommitConfirmations());
                        }
                    } else {
                        pendingTeleports.put(vehicleId,
                                new PendingTeleport(predictedLat, predictedLon, fraction, direction, 1, now));
                        teleportRejected = true;
                        log.info("[GPS_PIPELINE] TELEPORT_PENDING_RESET vehicle={} plate={} distFromPending={}m — snap jumped outside cluster, restarting pending",
                                vehicleId, licensePlate, String.format("%.0f", distToPending));
                    }
                } else {
                    pendingTeleports.put(vehicleId,
                            new PendingTeleport(predictedLat, predictedLon, fraction, direction, 1, now));
                    teleportRejected = true;
                    log.info("[GPS_PIPELINE] TELEPORT_PENDING_NEW vehicle={} plate={} count=1/{} at=({},{}) — keeping previous position",
                            vehicleId, licensePlate, properties.getTeleportCommitConfirmations(),
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
        boolean wasOffRoute = existing != null && !routeChanged && existing.isOffRoute();
        int newOffRouteCount = (existing != null && !routeChanged) ? existing.getConsecutiveOffRouteCount() : 0;
        boolean newOffRoute = wasOffRoute;
        double rawToSnapDist = snapResult.rawSnapMinDistance();
        boolean snapAttempted = rawToSnapDist < Double.MAX_VALUE;
        if (snapAttempted) {
            if (rawToSnapDist > properties.getOffRouteDistanceThresholdMeters()) {
                newOffRouteCount += 1;
                if (newOffRouteCount >= properties.getOffRouteConfirmations() && !newOffRoute) {
                    newOffRoute = true;
                    log.warn("[GPS_PIPELINE] OFF_ROUTE_DETECTED vehicle={} plate={} route={} consecutive={} rawToSnapDist={}m — suppressing broadcast until back on route",
                            vehicleId, licensePlate, routeNumber, newOffRouteCount,
                            String.format("%.0f", rawToSnapDist));
                }
            } else {
                if (newOffRoute) {
                    log.info("[GPS_PIPELINE] OFF_ROUTE_CLEARED vehicle={} plate={} route={} — vehicle returned within {}m of route",
                            vehicleId, licensePlate, routeNumber, (int) properties.getOffRouteDistanceThresholdMeters());
                }
                newOffRouteCount = 0;
                newOffRoute = false;
            }
        }

        Instant tickNow = clock.instant();
        Instant existingFirstOnRoute = (existing != null && !routeChanged) ? existing.getFirstOnRouteAtCurrentShift() : null;
        if (existingFirstOnRoute != null) {
            ShiftType currentShift = currentShiftAt(tickNow);
            ShiftType shiftAtFirst = currentShiftAt(existingFirstOnRoute);
            if (currentShift == null || shiftAtFirst == null || currentShift != shiftAtFirst) {
                existingFirstOnRoute = null;
            }
        }
        Instant newFirstOnRouteAtCurrentShift = existingFirstOnRoute;
        Instant newLastOnRouteAt = (existing != null && !routeChanged) ? existing.getLastOnRouteAt() : null;
        if (!newOffRoute) {
            if (newFirstOnRouteAtCurrentShift == null) {
                newFirstOnRouteAtCurrentShift = tickNow;
            }
            newLastOnRouteAt = tickNow;
        }

        double newLongTermAvg = PredictionMath.updateLongTermAvgSpeed(
                existing != null ? existing.getLongTermAvgSpeedKmh() : -1, speedKmh);

        double[] newKalman = PredictionMath.updateKalmanSpeed(
                existing != null ? existing.getKalmanSpeedKmh() : -1,
                existing != null ? existing.getKalmanSpeedVariance() : 0,
                speedKmh);

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
                .longTermAvgSpeedKmh(newLongTermAvg)
                .kalmanSpeedKmh(newKalman[0])
                .kalmanSpeedVariance(newKalman[1])
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
                .lastRawToSnapDistanceMeters(snapAttempted ? rawToSnapDist : Double.NaN)
                .firstOnRouteAtCurrentShift(newFirstOnRouteAtCurrentShift)
                .lastOnRouteAt(newLastOnRouteAt)
                .inGarage(false)
                .direction(direction)
                .directionConfirmed(directionConfirmed
                        || (existing != null && existing.isDirectionConfirmed())
                        || snapAttempted);

        boolean triggerColdStart = snapResult.resetTriggered() || positionTeleport;
        Instant coldStartUntilAt = triggerColdStart
                ? Instant.now().plusSeconds(properties.getColdStartDurationSec())
                : (existing != null ? existing.getColdStartUntilAt() : null);

        Instant directionChangedAt = existing != null ? existing.getDirectionChangedAt() : null;

        VehiclePredictionState builtState = builder
                .lastReceivedAt(Instant.now())
                .coldStartUntilAt(coldStartUntilAt)
                .directionChangedAt(directionChangedAt)
                .build();
        String writeReason = teleportRejected ? "onGpsUpdate-teleport-rejected"
                : (triggerColdStart ? (snapResult.resetTriggered() ? "onGpsUpdate-snap-reset" : "onGpsUpdate-pos-teleport")
                        : "onGpsUpdate-accept");
        replaceState(vehicleId, builtState, writeReason);
        if (!wasOffRoute && newOffRoute) {
            double offRouteLat = builtState.getGpsLatitude();
            double offRouteLon = builtState.getGpsLongitude();
            offRouteMonitor.ifPresent(m -> m.onWentOffRoute(
                    vehicleId, builtState, offRouteLat, offRouteLon, builtState.getLastRawToSnapDistanceMeters()));
        }
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

        Instant cycleStart = Instant.now();

        cleanupStaleStates();
        long cleanupMs = elapsedMs(cycleStart);

        Instant phaseStart = Instant.now();
        Instant now = Instant.now();
        long maxAgeMs = properties.getMaxAgeMs();
        double minSpeed = properties.getMinSpeedKmh();
        long stoppedIntervalMs = properties.getStoppedBroadcastIntervalMs();

        applyRouteBoundaryReset(minSpeed);
        long boundaryMs = elapsedMs(phaseStart);

        phaseStart = Instant.now();
        List<VehiclePredictionState> movingStates = collectMovingStates(now, maxAgeMs, minSpeed);
        List<VehiclePredictionState> stoppedStates = collectStoppedStates(now, maxAgeMs, minSpeed, stoppedIntervalMs);
        long collectMs = elapsedMs(phaseStart);

        if (movingStates.isEmpty() && stoppedStates.isEmpty()) {
            return Mono.empty();
        }

        long snapped = movingStates.stream().filter(s -> s.getFractionOnRoute() >= 0).count();
        long dr = movingStates.size() - snapped;
        log.debug("[GPS_PIPELINE] PRED_CYCLE moving={} (snapped={} dr={}) stopped={}",
                movingStates.size(), snapped, dr, stoppedStates.size());

        return advanceAndBroadcast(movingStates, stoppedStates, now, cycleStart, cleanupMs, boundaryMs, collectMs);
    }

    private void applyRouteBoundaryReset(double minSpeed) {
        vehicleStates.keySet().forEach(vid -> updateState(vid, current -> {
            if (isAtRouteBoundary(current)
                    && current.isInMotion()
                    && current.getSpeedKmh() >= minSpeed) {
                return current.toBuilder().fractionOnRoute(-1).build();
            }
            return null;
        }, "route-boundary-reset"));
    }

    private List<VehiclePredictionState> collectMovingStates(Instant now, long maxAgeMs, double minSpeed) {
        return vehicleStates.values().stream()
                .filter(state -> state.isInMotion() && state.getSpeedKmh() >= minSpeed)
                .filter(state -> state.getLastReceivedAt() != null
                        && (now.toEpochMilli() - state.getLastReceivedAt().toEpochMilli()) <= maxAgeMs)
                .filter(state -> state.getRouteCoordinates() != null
                        ? (state.getFractionOnRoute() >= 0
                                || PredictionBroadcaster.isInColdStart(state)
                                || state.isOffRoute())
                        : true)
                .toList();
    }

    private List<VehiclePredictionState> collectStoppedStates(Instant now, long maxAgeMs,
                                                               double minSpeed, long stoppedIntervalMs) {
        return vehicleStates.values().stream()
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
    }

    private Mono<Void> advanceAndBroadcast(List<VehiclePredictionState> movingStates,
                                            List<VehiclePredictionState> stoppedStates,
                                            Instant now,
                                            Instant cycleStart,
                                            long cleanupMs,
                                            long boundaryMs,
                                            long collectMs) {
        int parallelism = Math.max(1, Runtime.getRuntime().availableProcessors());
        Instant advanceStart = Instant.now();

        Flux<VehiclePredictionState> advancedMoving = movingStates.isEmpty()
                ? Flux.empty()
                : Flux.fromIterable(movingStates)
                        .parallel(parallelism)
                        .runOn(Schedulers.parallel())
                        .map(snapshot -> updateState(snapshot.getVehicleId(),
                                current -> predictor.advance(current)
                                        .toBuilder().lastBroadcastAt(now).build(),
                                "predictor-advance"))
                        .filter(Objects::nonNull)
                        .sequential();

        Flux<VehiclePredictionState> markedStopped = stoppedStates.isEmpty()
                ? Flux.empty()
                : Flux.fromIterable(stoppedStates)
                        .parallel(parallelism)
                        .runOn(Schedulers.parallel())
                        .map(snapshot -> updateState(snapshot.getVehicleId(),
                                current -> current.toBuilder().lastBroadcastAt(now).build(),
                                "stopped-mark-broadcast"))
                        .filter(Objects::nonNull)
                        .sequential();

        return Flux.concat(advancedMoving, markedStopped)
                .concatMap(broadcaster::broadcast)
                .then()
                .doFinally(sig -> logCycleTiming(cycleStart, cleanupMs, boundaryMs, collectMs,
                        advanceStart, movingStates.size(), stoppedStates.size()));
    }

    private void logCycleTiming(Instant cycleStart, long cleanupMs, long boundaryMs, long collectMs,
                                  Instant advanceStart, int movingCount, int stoppedCount) {
        long advanceBroadcastMs = elapsedMs(advanceStart);
        long totalMs = elapsedMs(cycleStart);
        if (totalMs > 1000) {
            log.warn("[GPS_PIPELINE] PRED_CYCLE_SLOW total={}ms cleanup={}ms boundary={}ms collect={}ms advance+broadcast={}ms moving={} stopped={}",
                    totalMs, cleanupMs, boundaryMs, collectMs, advanceBroadcastMs, movingCount, stoppedCount);
        } else {
            log.debug("[GPS_PIPELINE] PRED_CYCLE_TIMING total={}ms cleanup={}ms boundary={}ms collect={}ms advance+broadcast={}ms moving={} stopped={}",
                    totalMs, cleanupMs, boundaryMs, collectMs, advanceBroadcastMs, movingCount, stoppedCount);
        }
    }

    private static long elapsedMs(Instant from) {
        return Instant.now().toEpochMilli() - from.toEpochMilli();
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
                                "[GPS_PIPELINE] Redis state delete failed for stale vehicle {}: {}",
                                vehicleId, err.getMessage()));
            }
            return stale;
        });
        pendingTeleports.keySet().retainAll(vehicleStates.keySet());
        pendingDirectionChanges.keySet().retainAll(vehicleStates.keySet());
        pendingAltBaselines.keySet().retainAll(vehicleStates.keySet());
        lastDecisions.keySet().retainAll(vehicleStates.keySet());
        snapCorrector.onVehicleStaleCleanup(vehicleStates.keySet());
        broadcaster.onVehiclesStaleCleanup(vehicleStates.keySet());
    }

    static boolean isTrajectoryAdvance(double pendingFraction, int pendingDirection,
                                        double pendingLat, double pendingLon,
                                        double newFraction, int newDirection,
                                        double newLat, double newLon) {
        return isTrajectoryAdvance(pendingFraction, pendingDirection, pendingLat, pendingLon,
                newFraction, newDirection, newLat, newLon,
                0.1, 500.0);
    }

    static boolean isTrajectoryAdvance(double pendingFraction, int pendingDirection,
                                        double pendingLat, double pendingLon,
                                        double newFraction, int newDirection,
                                        double newLat, double newLon,
                                        double maxFracDelta, double maxStepMeters) {
        if (pendingFraction < 0 || newFraction < 0) {
            return false;
        }
        if (pendingDirection != newDirection) {
            return false;
        }
        double fracDelta = newFraction - pendingFraction;
        if (fracDelta <= 0 || fracDelta > maxFracDelta) {
            return false;
        }
        double dist = DistanceCalculationService.haversineDistanceMeters(
                pendingLat, pendingLon, newLat, newLon);
        return dist <= maxStepMeters;
    }

    private boolean isTrajectoryAdvance(PendingTeleport pending,
                                         double newFraction, int newDirection,
                                         double newLat, double newLon) {
        return isTrajectoryAdvance(pending.fraction(), pending.direction(),
                pending.lat(), pending.lon(),
                newFraction, newDirection, newLat, newLon,
                properties.getTeleportTrajectoryFracDeltaMax(),
                properties.getTeleportTrajectoryStepMeters());
    }

    private boolean shouldForceAcceptStaleBaseline(String vehicleId, String licensePlate,
                                                    double latitude, double longitude) {
        Instant now = Instant.now();
        OutlierBaseline current = pendingAltBaselines.get(vehicleId);
        if (current == null || now.toEpochMilli() - current.firstSeen().toEpochMilli() > properties.getForceAcceptWindowMs()) {
            pendingAltBaselines.put(vehicleId,
                    new OutlierBaseline(latitude, longitude, 1, now));
            return false;
        }
        double distFromCluster = DistanceCalculationService.haversineDistanceMeters(
                current.lat(), current.lon(), latitude, longitude);
        if (distFromCluster > properties.getForceAcceptClusterRadiusMeters()) {
            pendingAltBaselines.put(vehicleId,
                    new OutlierBaseline(latitude, longitude, 1, now));
            return false;
        }
        int newCount = current.count() + 1;
        if (newCount >= properties.getForceAcceptCount()) {
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

    void replaceStateForTest(String vehicleId, VehiclePredictionState state) {
        vehicleStates.put(vehicleId, state);
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

    private ShiftType currentShiftAt(Instant t) {
        LocalTime time = LocalTime.ofInstant(t, ZoneOffset.UTC);
        for (ShiftType s : ShiftType.values()) {
            if (s.isActiveAt(time)) return s;
        }
        return null;
    }

}
