package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.transport.domain.repository.StopDwellStatsRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.StopDwellStat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
class VehiclePositionPredictor {

    private static final double METRES_PER_DEGREE_LAT = 111_320.0;
    private static final double DT_SECONDS = 1.0;
    private static final double STOP_DECELERATION_TRIGGER_METERS = 300.0;
    private static final double DWELL_MIN_SECONDS = 3.0;
    private static final double DWELL_MAX_SECONDS = 600.0;
    private static final int DWELL_MIN_SAMPLES = 3;

    private static final double REAL_STOP_LONG_TERM_SPEED_KMH = 2.0;
    private static final double TRAFFIC_CRAWL_MIN_SPEED_KMH = 2.0;
    private static final double TRAFFIC_CRAWL_MAX_SPEED_KMH = 12.0;

    private static final double CATCH_UP_ERROR_THRESHOLD = 0.002;
    private static final double CATCH_UP_GAIN = 0.30;
    private static final double CATCH_UP_MAX_PER_TICK = 0.005;

    private final PredictionProperties properties;
    private final RouteGeometryCache routeGeometryCache;
    private final MapMatchingService mapMatchingService;
    private final StopDwellStatsRepository dwellStatsRepository;

    private final ConcurrentHashMap<String, StopDwellStat> dwellStatsCache = new ConcurrentHashMap<>();

    VehiclePositionPredictor(PredictionProperties properties,
                              RouteGeometryCache routeGeometryCache,
                              MapMatchingService mapMatchingService,
                              StopDwellStatsRepository dwellStatsRepository) {
        this.properties = properties;
        this.routeGeometryCache = routeGeometryCache;
        this.mapMatchingService = mapMatchingService;
        this.dwellStatsRepository = dwellStatsRepository;
    }

    Mono<Void> loadDwellStats() {
        return dwellStatsRepository.findAll()
                .doOnNext(stat -> dwellStatsCache.put(
                        dwellKey(stat.getStopId(), stat.getRouteNumber(), stat.getDirection()), stat))
                .doOnError(err -> log.warn("Failed to load dwell stats: {}", err.getMessage()))
                .onErrorResume(err -> Flux.empty())
                .then(Mono.fromRunnable(() ->
                        log.info("Loaded dwell stats cache: {} entries", dwellStatsCache.size())));
    }

    VehiclePredictionState advance(VehiclePredictionState state) {
        long msSinceGps = state.getLastReceivedAt() != null
                ? Instant.now().toEpochMilli() - state.getLastReceivedAt().toEpochMilli()
                : 0;

        if (msSinceGps > properties.getStopAdvanceAfterMs()) {
            return state;
        }

        if (PredictionBroadcaster.isInColdStart(state)) {
            return state;
        }

        if (state.getRouteNumber() == null || state.getRouteNumber().isBlank()) {
            return state;
        }

        if (state.getFractionOnRoute() < 0 && state.getLastGpsFraction() < 0) {
            return state;
        }

        boolean freshGps = msSinceGps < properties.getFreshGpsWindowMs();
        boolean rawBelowMin = state.getRawGpsSpeedKmh() < properties.getMinSpeedKmh();
        double longTermAvg = state.getLongTermAvgSpeedKmh();
        boolean realStop = freshGps && rawBelowMin
                && (longTermAvg < 0 || longTermAvg < REAL_STOP_LONG_TERM_SPEED_KMH);
        if (realStop) {
            log.debug("[GPS_PIPELINE] REAL_STOP vehicle={} plate={} rawSpeed={}km/h longTermAvg={}km/h — freezing predicted",
                    state.getVehicleId(), state.getLicensePlate(),
                    String.format("%.1f", state.getRawGpsSpeedKmh()),
                    longTermAvg >= 0 ? String.format("%.1f", longTermAvg) : "—");
            return state;
        }

        VehiclePredictionState result = advanceInternal(state, msSinceGps);
        if (result != state && state.getPredictedLatitude() != 0.0 && result.getPredictedLatitude() != 0.0) {
            double delta = biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService
                    .haversineDistanceMeters(state.getPredictedLatitude(), state.getPredictedLongitude(),
                            result.getPredictedLatitude(), result.getPredictedLongitude());
            if (delta > 500.0) {
                List<double[]> rc = state.getRouteCoordinates();
                int rcSize = rc != null ? rc.size() : -1;
                log.warn("[GPS_PIPELINE] ADVANCE_JUMP vehicle={} plate={} delta={}m speed={}km/h fraction={}->{} routeCoordsSize={} totalDist={} in.predicted=({},{}) out.predicted=({},{}) in.gps=({},{})",
                        state.getVehicleId(), state.getLicensePlate(),
                        String.format("%.0f", delta),
                        String.format("%.1f", state.getSpeedKmh()),
                        String.format("%.4f", state.getFractionOnRoute()),
                        String.format("%.4f", result.getFractionOnRoute()),
                        rcSize,
                        String.format("%.0f", state.getTotalRouteDistanceMeters()),
                        String.format("%.5f", state.getPredictedLatitude()),
                        String.format("%.5f", state.getPredictedLongitude()),
                        String.format("%.5f", result.getPredictedLatitude()),
                        String.format("%.5f", result.getPredictedLongitude()),
                        String.format("%.5f", state.getGpsLatitude()),
                        String.format("%.5f", state.getGpsLongitude()));
            }
        }
        return result;
    }

    private VehiclePredictionState advanceInternal(VehiclePredictionState state, long msSinceGps) {
        double baseSpeed = state.getSpeedKmh();
        boolean freshGps = msSinceGps < properties.getFreshGpsWindowMs();
        boolean rawBelowMin = state.getRawGpsSpeedKmh() < properties.getMinSpeedKmh();
        double longTermAvg = state.getLongTermAvgSpeedKmh();
        boolean trafficCrawl = freshGps && rawBelowMin
                && longTermAvg >= TRAFFIC_CRAWL_MIN_SPEED_KMH
                && longTermAvg <= TRAFFIC_CRAWL_MAX_SPEED_KMH;
        if (trafficCrawl) {
            log.debug("[GPS_PIPELINE] TRAFFIC_CRAWL vehicle={} plate={} rawSpeed={}km/h longTermAvg={}km/h — using crawl speed for advance",
                    state.getVehicleId(), state.getLicensePlate(),
                    String.format("%.1f", state.getRawGpsSpeedKmh()),
                    String.format("%.1f", longTermAvg));
            baseSpeed = longTermAvg;
        }

        double decayedSpeedKmh = baseSpeed * decayFactor(msSinceGps);
        double conservativeFactor = properties.getConservativeSpeedFactor();
        double adjustedConservative = conservativeFactor;

        List<double[]> routeCoords = state.getRouteCoordinates();
        double totalRouteDistance = state.getTotalRouteDistanceMeters();
        double effectiveStartFraction = state.getFractionOnRoute() >= 0
                ? state.getFractionOnRoute()
                : state.getLastGpsFraction();
        boolean onRoute = routeCoords != null && effectiveStartFraction >= 0 && totalRouteDistance > 0;

        if (onRoute) {
            double distToNextStop = computeDistanceToNextStop(state, totalRouteDistance);
            if (distToNextStop >= 0 && distToNextStop < STOP_DECELERATION_TRIGGER_METERS) {
                adjustedConservative = conservativeFactor
                        + (1.0 - conservativeFactor) * (1.0 - distToNextStop / STOP_DECELERATION_TRIGGER_METERS);
            }
        }

        double effectiveSpeedKmh = decayedSpeedKmh * adjustedConservative;

        if (onRoute) {
            if (state.getDwellStartedAt() != null) {
                return advanceDuringDwell(state);
            }

            double stopDecel = computeStopDecelerationFactor(state, totalRouteDistance);
            double stopAccel = computeStopAccelerationFactor(state, totalRouteDistance);
            double stopFactor = Math.min(stopDecel, stopAccel);
            double speedMs = (effectiveSpeedKmh * stopFactor) / 3.6;
            double fractionDelta = speedMs * DT_SECONDS / totalRouteDistance;
            double newFraction = Math.min(effectiveStartFraction + fractionDelta, 1.0);

            double lastGpsFrac = state.getLastGpsFraction();
            if (lastGpsFrac >= 0 && lastGpsFrac > newFraction + CATCH_UP_ERROR_THRESHOLD) {
                double trackingError = lastGpsFrac - newFraction;
                double catchUpBoost = Math.min(trackingError * CATCH_UP_GAIN, CATCH_UP_MAX_PER_TICK);
                double before = newFraction;
                newFraction = Math.min(newFraction + catchUpBoost, 1.0);
                log.debug("[GPS_PIPELINE] CATCH_UP vehicle={} plate={} trackingError={} boost={} fraction={}→{} (lastGps={})",
                        state.getVehicleId(), state.getLicensePlate(),
                        String.format("%.4f", trackingError),
                        String.format("%.4f", catchUpBoost),
                        String.format("%.4f", before),
                        String.format("%.4f", newFraction),
                        String.format("%.4f", lastGpsFrac));
            }

            VehiclePredictionState dwellTriggered = tryTriggerDwell(state, routeCoords, totalRouteDistance);
            if (dwellTriggered != null) {
                return dwellTriggered;
            }

            double[] coords = mapMatchingService.interpolateRoutePoint(routeCoords, newFraction, totalRouteDistance);
            if (coords == null) return state;

            if (state.getPredictedLatitude() != 0.0) {
                double snapToPredicted = biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService
                        .haversineDistanceMeters(state.getPredictedLatitude(), state.getPredictedLongitude(),
                                coords[0], coords[1]);
                if (snapToPredicted > 300.0) {
                    int newCount = state.getConsecutiveInconsistentAdvanceCount() + 1;
                    if (newCount >= 3) {
                        log.warn("[GPS_PIPELINE] ADVANCE_INCONSISTENT_STATE_RESET vehicle={} plate={} consecutiveInconsistent={} driftDist={}m — clearing fraction/lastGpsFraction and triggering cold-start to escape polyline-discontinuity loop",
                                state.getVehicleId(), state.getLicensePlate(), newCount,
                                String.format("%.0f", snapToPredicted));
                        return state.toBuilder()
                                .fractionOnRoute(-1)
                                .lastGpsFraction(-1)
                                .lastRejectedGpsFraction(-1)
                                .consecutiveImplausibleCount(0)
                                .consecutiveInconsistentAdvanceCount(0)
                                .offRoute(false)
                                .consecutiveOffRouteCount(0)
                                .coldStartUntilAt(Instant.now().plusSeconds(properties.getColdStartDurationSec()))
                                .build();
                    }
                    log.warn("[GPS_PIPELINE] ADVANCE_INCONSISTENT_STATE vehicle={} plate={} predicted=({},{}) interpolated=({},{}) frac={} driftDist={}m count={}/3 — holding predicted, awaiting GPS re-sync (not snapping to interpolated which may be polyline artifact)",
                            state.getVehicleId(), state.getLicensePlate(),
                            String.format("%.5f", state.getPredictedLatitude()),
                            String.format("%.5f", state.getPredictedLongitude()),
                            String.format("%.5f", coords[0]),
                            String.format("%.5f", coords[1]),
                            String.format("%.4f", state.getFractionOnRoute()),
                            String.format("%.0f", snapToPredicted),
                            newCount);
                    return state.toBuilder()
                            .consecutiveInconsistentAdvanceCount(newCount)
                            .speedKmh(decayedSpeedKmh)
                            .build();
                }
            }

            double newCourse = mapMatchingService.calculateCourseFromRoute(
                    routeCoords, newFraction, state.getDirection(), totalRouteDistance);

            return state.toBuilder()
                    .speedKmh(decayedSpeedKmh)
                    .predictedLatitude(coords[0])
                    .predictedLongitude(coords[1])
                    .fractionOnRoute(newFraction)
                    .course(newCourse)
                    .consecutiveInconsistentAdvanceCount(0)
                    .build();
        }

        return advanceDeadReckoning(state, decayedSpeedKmh, effectiveSpeedKmh);
    }

    private double decayFactor(long msSinceGps) {
        if (msSinceGps <= properties.getFreshGpsWindowMs()) return 1.0;
        if (msSinceGps <= properties.getAggressiveDecayAfterMs()) return properties.getDecayFactor();
        return properties.getAggressiveDecayFactor();
    }

    private VehiclePredictionState advanceDuringDwell(VehiclePredictionState state) {
        long dwellMs = Instant.now().toEpochMilli() - state.getDwellStartedAt().toEpochMilli();
        double expectedDwellSec = getHistoricalDwellSeconds(
                state.getDwellStopId(), state.getRouteNumber(), state.getDirection());
        boolean dwellExpired = dwellMs >= (long) (expectedDwellSec * 1000);
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

    private VehiclePredictionState tryTriggerDwell(VehiclePredictionState state,
                                                    List<double[]> routeCoords,
                                                    double totalRouteDistance) {
        double trueFrac = state.getLastGpsFraction() >= 0
                ? state.getLastGpsFraction()
                : state.getFractionOnRoute();
        double distToNextStopTrue = computeDistanceToNextStopFromFraction(trueFrac, state, totalRouteDistance);
        boolean shouldDwell = distToNextStopTrue >= 0
                && distToNextStopTrue < properties.getDwellActivationDistanceMeters()
                && state.getRawGpsSpeedKmh() < properties.getDwellSpeedThresholdKmh();
        if (!shouldDwell) return null;

        Optional<biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo> nextStopOpt =
                routeGeometryCache.getNextStop(state.getRouteNumber(), state.getDirection(), trueFrac);
        if (nextStopOpt.isEmpty()) return null;

        var nextStop = nextStopOpt.get();
        double nextStopFrac = nextStop.getDistanceFromStartMeters() / totalRouteDistance;
        log.info("[GPS_PIPELINE] DWELL_START vehicle={} plate={} stop={} stop_frac={} dist={}m gpsSpeed={}km/h",
                state.getVehicleId(), state.getLicensePlate(),
                nextStop.getStopId(),
                String.format("%.4f", nextStopFrac),
                String.format("%.0f", distToNextStopTrue),
                String.format("%.1f", state.getRawGpsSpeedKmh()));
        double[] stopCoords = mapMatchingService.interpolateRoutePoint(routeCoords, nextStopFrac, totalRouteDistance);
        if (stopCoords == null) return null;

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

    private VehiclePredictionState advanceDeadReckoning(VehiclePredictionState state,
                                                         double decayedSpeedKmh,
                                                         double effectiveSpeedKmh) {
        double speedMs = effectiveSpeedKmh / 3.6;
        double courseRad = Math.toRadians(state.getCourse());
        double dNorth = speedMs * DT_SECONDS * Math.cos(courseRad);
        double dEast = speedMs * DT_SECONDS * Math.sin(courseRad);
        double dLat = dNorth / METRES_PER_DEGREE_LAT;
        double dLon = dEast / (METRES_PER_DEGREE_LAT * Math.cos(Math.toRadians(state.getPredictedLatitude())));

        return state.toBuilder()
                .speedKmh(decayedSpeedKmh)
                .predictedLatitude(state.getPredictedLatitude() + dLat)
                .predictedLongitude(state.getPredictedLongitude() + dLon)
                .build();
    }

    private String dwellKey(String stopId, String routeNumber, int direction) {
        return stopId + ":" + routeNumber + ":" + direction;
    }

    private double getHistoricalDwellSeconds(String stopId, String routeNumber, int direction) {
        if (stopId == null || routeNumber == null) {
            return properties.getDwellTimeSeconds();
        }
        var stat = dwellStatsCache.get(dwellKey(stopId, routeNumber, direction));
        if (stat == null || stat.getSampleCount() < DWELL_MIN_SAMPLES) {
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
        if (dwellSec < DWELL_MIN_SECONDS || dwellSec > DWELL_MAX_SECONDS) {
            log.debug("[DWELL] skip record out-of-range: stop={} dwell={}s", stopId, dwellSec);
            return;
        }

        String key = dwellKey(stopId, state.getRouteNumber(), state.getDirection());
        var existing = dwellStatsCache.get(key);
        var updated = existing != null
                ? existing.withNewSample(dwellSec, Instant.now())
                : StopDwellStat.initial(stopId, state.getRouteNumber(), state.getDirection())
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
