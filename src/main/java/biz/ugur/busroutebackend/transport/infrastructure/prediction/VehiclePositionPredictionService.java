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

    private final ConcurrentHashMap<String, VehiclePredictionState> vehicleStates = new ConcurrentHashMap<>();

    private final PredictionProperties properties;
    private final PredictionBroadcaster broadcaster;
    private final RouteGeometryCache routeGeometryCache;
    private final MapMatchingService mapMatchingService;
    private final VehiclePredictionStateRepository stateRepository;
    private final biz.ugur.busroutebackend.transport.domain.repository.StopDwellStatsRepository dwellStatsRepository;
    private final ObjectProvider<GpsRecorder> gpsRecorderProvider;
    private final GpsOutlierFilter outlierFilter;
    private final SnapCorrector snapCorrector;

    private final ConcurrentHashMap<String, biz.ugur.busroutebackend.transport.domain.valueobject.StopDwellStat> dwellStatsCache
            = new ConcurrentHashMap<>();

    public VehiclePositionPredictionService(PredictionProperties properties,
                                             PredictionBroadcaster broadcaster,
                                             RouteGeometryCache routeGeometryCache,
                                             MapMatchingService mapMatchingService,
                                             VehiclePredictionStateRepository stateRepository,
                                             biz.ugur.busroutebackend.transport.domain.repository.StopDwellStatsRepository dwellStatsRepository,
                                             ObjectProvider<GpsRecorder> gpsRecorderProvider,
                                             GpsOutlierFilter outlierFilter,
                                             SnapCorrector snapCorrector) {
        this.properties = properties;
        this.broadcaster = broadcaster;
        this.routeGeometryCache = routeGeometryCache;
        this.mapMatchingService = mapMatchingService;
        this.stateRepository = stateRepository;
        this.dwellStatsRepository = dwellStatsRepository;
        this.gpsRecorderProvider = gpsRecorderProvider;
        this.outlierFilter = outlierFilter;
        this.snapCorrector = snapCorrector;
    }

    private static final Duration RESTORE_TIMEOUT = Duration.ofSeconds(30);

    @EventListener(ApplicationReadyEvent.class)
    public void restoreFromRedis() {
        if (!properties.isEnabled()) return;

        try {
            Mono.when(loadDwellStats(), loadPredictionStates())
                    .subscribeOn(Schedulers.boundedElastic())
                    .block(RESTORE_TIMEOUT);
        } catch (RuntimeException e) {
            log.warn("Prediction state restore aborted after {}s, continuing with empty cache: {}",
                    RESTORE_TIMEOUT.toSeconds(), e.getMessage());
        }
    }

    private Mono<Void> loadDwellStats() {
        return dwellStatsRepository.findAll()
                .doOnNext(stat -> dwellStatsCache.put(
                        dwellKey(stat.getStopId(), stat.getRouteNumber(), stat.getDirection()), stat))
                .doOnError(err -> log.warn("Failed to load dwell stats: {}", err.getMessage()))
                .onErrorResume(err -> Flux.empty())
                .then(Mono.fromRunnable(() ->
                        log.info("Loaded dwell stats cache: {} entries", dwellStatsCache.size())));
    }

    private Mono<Void> loadPredictionStates() {
        return stateRepository.loadAll()
                .filter(state -> state.getVehicleId() != null)
                .map(this::attachRouteGeometry)
                .map(state -> state.toBuilder()
                        .lastReceivedAt(Instant.now().minusSeconds(properties.getMaxAgeMs() / 1000 + 60))
                        .build())
                .doOnNext(state -> {
                    vehicleStates.put(state.getVehicleId(), state);
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
                            int direction) {
        if (!properties.isEnabled()) {
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
        switch (outlierDecision) {
            case REJECT_HARD_OUTLIER, REJECT_SOFT_OUTLIER -> {
                vehicleStates.put(vehicleId, existing.toBuilder()
                        .lastReceivedAt(Instant.now())
                        .build());
                return;
            }
            case REJECT_TELEPORT_GAP -> {
                vehicleStates.put(vehicleId, existing.toBuilder()
                        .lastGpsUpdate(timestamp)
                        .lastReceivedAt(Instant.now())
                        .build());
                return;
            }
            case ACCEPT -> { /* fall through to snap/predict */ }
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
            if (isAtRouteBoundary(state)
                    && state.isInMotion()
                    && state.getSpeedKmh() >= minSpeed) {
                vehicleStates.put(state.getVehicleId(),
                        state.toBuilder().fractionOnRoute(-1).build());
            }
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
                .flatMap(state -> {
                    VehiclePredictionState advanced = advanceState(state);
                    advanced = advanced.toBuilder().lastBroadcastAt(now).build();
                    vehicleStates.put(advanced.getVehicleId(), advanced);
                    return broadcaster.broadcast(advanced);
                })
                .then();

        Mono<Void> stoppedMono = Flux.fromIterable(stoppedStates)
                .flatMap(state -> {
                    VehiclePredictionState marked = state.toBuilder().lastBroadcastAt(now).build();
                    vehicleStates.put(marked.getVehicleId(), marked);
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
                stateRepository.delete(e.getKey())
                        .subscribeOn(Schedulers.boundedElastic())
                        .subscribe();
            }
            return stale;
        });
        snapCorrector.onVehicleStaleCleanup(vehicleStates.keySet());
    }

    private boolean isAtRouteBoundary(VehiclePredictionState state) {
        if (state.getRouteCoordinates() == null || state.getFractionOnRoute() < 0) {
            return false;
        }
        return state.getFractionOnRoute() >= 1.0;
    }

    private VehiclePredictionState advanceState(VehiclePredictionState state) {
        return advancePositionOnly(state);
    }

    private VehiclePredictionState advancePositionOnly(VehiclePredictionState state) {
        double conservativeFactor = properties.getConservativeSpeedFactor();

        long msSinceGps = state.getLastReceivedAt() != null
                ? Instant.now().toEpochMilli() - state.getLastReceivedAt().toEpochMilli()
                : 0;

        if (msSinceGps > properties.getStopAdvanceAfterMs()) {
            return state;
        }

        double decayFactor;
        if (msSinceGps <= properties.getFreshGpsWindowMs()) {
            decayFactor = 1.0; 
        } else if (msSinceGps <= properties.getAggressiveDecayAfterMs()) {
            decayFactor = properties.getDecayFactor();
        } else {
            decayFactor = properties.getAggressiveDecayFactor();
        }

        double decayedSpeedKmh = state.getSpeedKmh() * decayFactor;

        double adjustedConservative = conservativeFactor;
        List<double[]> routeCoords = state.getRouteCoordinates();
        double totalRouteDistance = state.getTotalRouteDistanceMeters();

        if (routeCoords != null && state.getFractionOnRoute() >= 0 && totalRouteDistance > 0) {
            double distToNextStop = computeDistanceToNextStop(state, totalRouteDistance);
            if (distToNextStop >= 0 && distToNextStop < 300.0) {
                adjustedConservative = conservativeFactor + (1.0 - conservativeFactor) * (1.0 - distToNextStop / 300.0);
            }
        }

        double effectiveSpeedKmh = decayedSpeedKmh * adjustedConservative;

        if (routeCoords != null && state.getFractionOnRoute() >= 0 && totalRouteDistance > 0) {
            if (state.getDwellStartedAt() != null) {
                long dwellMs = Instant.now().toEpochMilli() - state.getDwellStartedAt().toEpochMilli();
                double expectedDwellSec = getHistoricalDwellSeconds(
                        state.getDwellStopId(), state.getRouteNumber(), state.getDirection());
                boolean dwellExpired = dwellMs >= (long)(expectedDwellSec * 1000);
                boolean gpsShowsMovement = state.getRawGpsSpeedKmh() >= properties.getDwellSpeedThresholdKmh();
                if (!dwellExpired && !gpsShowsMovement) {
                    return state;
                }
                recordDwellObservation(state, dwellMs);
                double resumeSpeed = Math.max(state.getRawGpsSpeedKmh(), state.getSmoothedSpeedKmh());
                return state.toBuilder()
                        .dwellStartedAt(null)
                        .dwellStopFraction(-1)
                        .dwellStopId(null)
                        .speedKmh(resumeSpeed)
                        .build();
            }

            double stopDecel = computeStopDecelerationFactor(state, totalRouteDistance);
            double stopAccel = computeStopAccelerationFactor(state, totalRouteDistance);
            double stopFactor = Math.min(stopDecel, stopAccel);
            double speedMs = (effectiveSpeedKmh * stopFactor) / 3.6;
            double fractionDelta = speedMs * DT_SECONDS / totalRouteDistance;

            double newFraction = Math.min(state.getFractionOnRoute() + fractionDelta, 1.0);

            double trueFrac = state.getLastGpsFraction() >= 0
                    ? state.getLastGpsFraction()
                    : state.getFractionOnRoute();
            double distToNextStopTrue = computeDistanceToNextStopFromFraction(trueFrac, state, totalRouteDistance);
            if (distToNextStopTrue >= 0 && distToNextStopTrue < properties.getDwellActivationDistanceMeters()
                    && state.getRawGpsSpeedKmh() < properties.getDwellSpeedThresholdKmh()) {
                java.util.Optional<biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo> nextStopOpt =
                        routeGeometryCache.getNextStop(state.getRouteNumber(), state.getDirection(), trueFrac);
                if (nextStopOpt.isPresent()) {
                    var nextStop = nextStopOpt.get();
                    double nextStopFrac = nextStop.getDistanceFromStartMeters() / totalRouteDistance;
                    log.info("[GPS_PIPELINE] DWELL_START vehicle={} plate={} stop={} stop_frac={} dist={}m gpsSpeed={}km/h",
                            state.getVehicleId(), state.getLicensePlate(),
                            nextStop.getStopId(),
                            String.format("%.4f", nextStopFrac),
                            String.format("%.0f", distToNextStopTrue),
                            String.format("%.1f", state.getRawGpsSpeedKmh()));
                    double[] stopCoords = mapMatchingService.interpolateRoutePoint(routeCoords, nextStopFrac, totalRouteDistance);
                    if (stopCoords != null) {
                        return state.toBuilder()
                                .predictedLatitude(stopCoords[0])
                                .predictedLongitude(stopCoords[1])
                                .fractionOnRoute(nextStopFrac)
                                .dwellStartedAt(Instant.now())
                                .dwellStopFraction(nextStopFrac)
                                .dwellStopId(nextStop.getStopId())
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

    public Map<String, Integer> drainPendingDirectionFixes() {
        return snapCorrector.drainPendingDirectionFixes();
    }

    private String dwellKey(String stopId, String routeNumber, int direction) {
        return stopId + ":" + routeNumber + ":" + direction;
    }

    private double getHistoricalDwellSeconds(String stopId, String routeNumber, int direction) {
        if (stopId == null || routeNumber == null) {
            return properties.getDwellTimeSeconds();
        }
        var stat = dwellStatsCache.get(dwellKey(stopId, routeNumber, direction));
        if (stat == null || stat.getSampleCount() < 3) {
            return properties.getDwellTimeSeconds();
        }
        return stat.getAvgDwellSeconds();
    }

   
    private void recordDwellObservation(VehiclePredictionState state, long dwellMs) {
        String stopId = state.getDwellStopId();
        if (stopId == null || state.getRouteNumber() == null) {
            return;
        }
        double dwellSec = dwellMs / 1000.0;
        if (dwellSec < 3 || dwellSec > 600) {
            log.debug("[DWELL] skip record out-of-range: stop={} dwell={}s", stopId, dwellSec);
            return;
        }

        String key = dwellKey(stopId, state.getRouteNumber(), state.getDirection());
        var existing = dwellStatsCache.get(key);
        var updated = existing != null
                ? existing.withNewSample(dwellSec, Instant.now())
                : biz.ugur.busroutebackend.transport.domain.valueobject.StopDwellStat
                        .initial(stopId, state.getRouteNumber(), state.getDirection())
                        .withNewSample(dwellSec, Instant.now());

        dwellStatsCache.put(key, updated);

        log.info("[DWELL] record stop={} route={} dir={} dwell={}s avg={}s samples={}",
                stopId, state.getRouteNumber(), state.getDirection(),
                String.format("%.1f", dwellSec),
                String.format("%.1f", updated.getAvgDwellSeconds()),
                updated.getSampleCount());

        dwellStatsRepository.save(updated)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        err -> log.warn("[DWELL] failed to persist: stop={} err={}", stopId, err.getMessage())
                );
    }

    private double computeDistanceToNextStopFromFraction(double fraction, VehiclePredictionState state, double totalRouteDistance) {
        if (fraction < 0 || state.getRouteNumber() == null || totalRouteDistance <= 0) return -1;
        double[] stopFractions = routeGeometryCache.getStopFractions(state.getRouteNumber(), state.getDirection());
        if (stopFractions == null || stopFractions.length == 0) return -1;
        double nextStopFrac = PredictionMath.findNextStopFraction(stopFractions, fraction);
        if (nextStopFrac < 0) return -1;
        return Math.abs(nextStopFrac - fraction) * totalRouteDistance;
    }

    private double computeDistanceToNextStop(VehiclePredictionState state, double totalRouteDistance) {
        if (state.getRouteNumber() == null || totalRouteDistance <= 0) return -1;
        double[] stopFractions = routeGeometryCache.getStopFractions(state.getRouteNumber(), state.getDirection());
        if (stopFractions == null || stopFractions.length == 0) return -1;
        double currentFraction = state.getFractionOnRoute();
        double nextStopFraction = PredictionMath.findNextStopFraction(stopFractions, currentFraction);
        if (nextStopFraction < 0) return -1;
        return Math.abs(nextStopFraction - currentFraction) * totalRouteDistance;
    }

    private double computeStopDecelerationFactor(VehiclePredictionState state, double totalRouteDistance) {
        if (state.getRouteNumber() == null) return 1.0;

        double[] stopFractions = routeGeometryCache.getStopFractions(state.getRouteNumber(), state.getDirection());
        if (stopFractions == null || stopFractions.length == 0) return 1.0;

        double currentFraction = state.getFractionOnRoute();
        double nextStopFraction = PredictionMath.findNextStopFraction(stopFractions, currentFraction);
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

   
    private double computeStopAccelerationFactor(VehiclePredictionState state, double totalRouteDistance) {
        if (state.getRouteNumber() == null) return 1.0;
        double[] stopFractions = routeGeometryCache.getStopFractions(state.getRouteNumber(), state.getDirection());
        if (stopFractions == null || stopFractions.length == 0) return 1.0;

        double currentFraction = state.getFractionOnRoute();
        double prevStopFrac = PredictionMath.findPreviousStopFraction(stopFractions, currentFraction);
        if (prevStopFrac < 0) return 1.0;

        double distFromStop = Math.abs(currentFraction - prevStopFrac) * totalRouteDistance;
        double zone = properties.getStopAccelerationZoneMeters();
        if (distFromStop >= zone) return 1.0;

        double minFactor = properties.getStopAccelerationMinFactor();
        return minFactor + (1.0 - minFactor) * (distFromStop / zone);
    }

}
