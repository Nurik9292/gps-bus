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

        Instant coldStartUntilAt = snapResult.resetTriggered()
                ? Instant.now().plusSeconds(properties.getColdStartDurationSec())
                : (existing != null ? existing.getColdStartUntilAt() : null);

        VehiclePredictionState builtState = builder
                .lastReceivedAt(Instant.now())
                .coldStartUntilAt(coldStartUntilAt)
                .build();
        vehicleStates.put(vehicleId, builtState);
        if (snapResult.resetTriggered()) {
            log.warn("[GPS_PIPELINE] COLD_START vehicle={} plate={} route={} duration={}s — WS broadcast suppressed until state stabilizes",
                    vehicleId, licensePlate, routeNumber, properties.getColdStartDurationSec());
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
                    VehiclePredictionState advanced = predictor.advance(state);
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

    public Map<String, Integer> drainPendingDirectionFixes() {
        return snapCorrector.drainPendingDirectionFixes();
    }


}
