package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.transport.domain.repository.SegmentTravelStatsRepository;
import biz.ugur.busroutebackend.transport.domain.repository.StopDwellStatsRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.SegmentTravelStat;
import biz.ugur.busroutebackend.transport.domain.valueobject.StopDwellStat;
import biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
class VehiclePositionPredictor {

    private static final double METRES_PER_DEGREE_LAT = 111_320.0;
    private static final double DT_SECONDS = 1.0;



    private final PredictionProperties properties;
    private final RouteGeometryCache routeGeometryCache;
    private final MapMatchingService mapMatchingService;
    private final StopDwellStatsRepository dwellStatsRepository;
    private final SegmentTravelStatsRepository segmentTravelStatsRepository;
    private final PipelineTracer pipelineTracer;

    private final ConcurrentHashMap<String, StopDwellStat> dwellStatsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SegmentTravelStat> segmentTravelStatsCache = new ConcurrentHashMap<>();

    VehiclePositionPredictor(PredictionProperties properties,
                              RouteGeometryCache routeGeometryCache,
                              MapMatchingService mapMatchingService,
                              StopDwellStatsRepository dwellStatsRepository,
                              SegmentTravelStatsRepository segmentTravelStatsRepository,
                              PipelineTracer pipelineTracer) {
        this.properties = properties;
        this.routeGeometryCache = routeGeometryCache;
        this.mapMatchingService = mapMatchingService;
        this.dwellStatsRepository = dwellStatsRepository;
        this.segmentTravelStatsRepository = segmentTravelStatsRepository;
        this.pipelineTracer = pipelineTracer;
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

    Mono<Void> loadSegmentTravelStats() {
        return segmentTravelStatsRepository.findAll()
                .doOnNext(stat -> segmentTravelStatsCache.put(segmentKey(stat), stat))
                .doOnError(err -> log.warn("[GPS_PIPELINE] Failed to load segment travel stats: {}", err.getMessage()))
                .onErrorResume(err -> Flux.empty())
                .then(Mono.fromRunnable(() ->
                        log.info("[GPS_PIPELINE] Loaded segment travel stats cache: {} entries",
                                segmentTravelStatsCache.size())));
    }

    SegmentTravelStat getSegmentTravelStat(String routeNumber, int direction,
                                           String fromStopId, String toStopId,
                                           int hourOfDay, boolean weekend) {
        return segmentTravelStatsCache.get(
                segmentKey(routeNumber, direction, fromStopId, toStopId, hourOfDay, weekend));
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
                && (longTermAvg < 0 || longTermAvg < properties.getRealStopLongTermSpeedKmh());
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
        double baseSpeed = state.getKalmanSpeedKmh() >= 0
                ? state.getKalmanSpeedKmh()
                : state.getSpeedKmh();
        boolean freshGps = msSinceGps < properties.getFreshGpsWindowMs();
        boolean rawBelowMin = state.getRawGpsSpeedKmh() < properties.getMinSpeedKmh();
        double longTermAvg = state.getLongTermAvgSpeedKmh();
        boolean trafficCrawl = freshGps && rawBelowMin
                && longTermAvg >= properties.getTrafficCrawlMinSpeedKmh()
                && longTermAvg <= properties.getTrafficCrawlMaxSpeedKmh();
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
            if (distToNextStop >= 0 && distToNextStop < properties.getStopDecelerationTriggerMeters()) {
                adjustedConservative = conservativeFactor
                        + (1.0 - conservativeFactor) * (1.0 - distToNextStop / properties.getStopDecelerationTriggerMeters());
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
            if (lastGpsFrac >= 0 && lastGpsFrac > newFraction + properties.getCatchUpErrorThreshold()) {
                double trackingError = lastGpsFrac - newFraction;
                double catchUpBoost = Math.min(trackingError * properties.getCatchUpGain(), properties.getCatchUpMaxPerTick());
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

            double[] cumDistAdvance = routeGeometryCache.getCumulativeDistances(state.getRouteId(), state.getDirection());
            double[] coords = mapMatchingService.interpolateRoutePoint(routeCoords, cumDistAdvance, newFraction, totalRouteDistance);
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
                    routeCoords, cumDistAdvance, newFraction, state.getDirection(), totalRouteDistance);

            double driftFromRawGps = (state.getGpsLatitude() != 0.0 && state.getGpsLongitude() != 0.0)
                    ? DistanceCalculationService.haversineDistanceMeters(
                            coords[0], coords[1], state.getGpsLatitude(), state.getGpsLongitude())
                    : 0.0;
            pipelineTracer.tracePredictorAdvance(
                    state.getVehicleId(), state.getLicensePlate(), state.getRouteNumber(),
                    state.getDirection(),
                    state.getFractionOnRoute(), newFraction,
                    state.getPredictedLatitude(), state.getPredictedLongitude(),
                    coords[0], coords[1],
                    state.getGpsLatitude(), state.getGpsLongitude(),
                    driftFromRawGps,
                    decayedSpeedKmh, msSinceGps);

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
                .lastSegmentDepartureAt(Instant.now())
                .lastSegmentDepartureStopId(state.getDwellStopId())
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
                routeGeometryCache.getNextStop(state.getRouteId(), state.getDirection(), trueFrac);
        if (nextStopOpt.isEmpty()) return null;

        var nextStop = nextStopOpt.get();
        double nextStopFrac = nextStop.getDistanceFromStartMeters() / totalRouteDistance;
        log.info("[GPS_PIPELINE] DWELL_START vehicle={} plate={} stop={} stop_frac={} dist={}m gpsSpeed={}km/h",
                state.getVehicleId(), state.getLicensePlate(),
                nextStop.getStopId(),
                String.format("%.4f", nextStopFrac),
                String.format("%.0f", distToNextStopTrue),
                String.format("%.1f", state.getRawGpsSpeedKmh()));

        recordSegmentTravelObservation(state, nextStop.getStopId());

        double[] dwellCumDist = routeGeometryCache.getCumulativeDistances(state.getRouteId(), state.getDirection());
        double[] stopCoords = mapMatchingService.interpolateRoutePoint(routeCoords, dwellCumDist, nextStopFrac, totalRouteDistance);
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
        if (stat == null || stat.getSampleCount() < properties.getDwellMinSamples()) {
            return properties.getDwellTimeSeconds();
        }
        return stat.getAvgDwellSeconds();
    }

    private static String segmentKey(SegmentTravelStat stat) {
        return segmentKey(stat.getRouteNumber(), stat.getDirection(),
                stat.getFromStopId(), stat.getToStopId(),
                stat.getHourOfDay(), stat.isWeekend());
    }

    private static String segmentKey(String routeNumber, int direction,
                                      String fromStopId, String toStopId,
                                      int hourOfDay, boolean weekend) {
        return routeNumber + ":" + direction + ":" + fromStopId + "->" + toStopId
                + ":h" + hourOfDay + ":" + (weekend ? "we" : "wd");
    }

    private void recordSegmentTravelObservation(VehiclePredictionState state, String toStopId) {
        String fromStopId = state.getLastSegmentDepartureStopId();
        Instant departureAt = state.getLastSegmentDepartureAt();
        if (fromStopId == null || departureAt == null
                || state.getRouteNumber() == null
                || toStopId == null
                || fromStopId.equals(toStopId)) {
            return;
        }

        long elapsedMs = Instant.now().toEpochMilli() - departureAt.toEpochMilli();
        double elapsedSec = elapsedMs / 1000.0;
        if (elapsedSec < properties.getDwellMinSeconds() || elapsedSec > properties.getDwellMaxSeconds() * 4) {
            log.debug("[GPS_PIPELINE] SEGMENT_TRAVEL_SKIP_OUT_OF_RANGE route={} dir={} {}->{}: elapsed={}s",
                    state.getRouteNumber(), state.getDirection(), fromStopId, toStopId,
                    String.format("%.1f", elapsedSec));
            return;
        }

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ashgabat"));
        int hourOfDay = now.getHour();
        boolean weekend = now.getDayOfWeek() == DayOfWeek.SATURDAY
                || now.getDayOfWeek() == DayOfWeek.SUNDAY;
        String key = segmentKey(state.getRouteNumber(), state.getDirection(),
                fromStopId, toStopId, hourOfDay, weekend);

        SegmentTravelStat existing = segmentTravelStatsCache.get(key);
        SegmentTravelStat updated = (existing != null
                ? existing
                : SegmentTravelStat.initial(state.getRouteNumber(), state.getDirection(),
                        fromStopId, toStopId, hourOfDay, weekend))
                .withNewSample(elapsedSec, Instant.now());

        segmentTravelStatsCache.put(key, updated);

        log.info("[GPS_PIPELINE] SEGMENT_TRAVEL_OBSERVED route={} dir={} {}->{} hour={} weekend={} elapsed={}s avg={}s samples={}",
                state.getRouteNumber(), state.getDirection(), fromStopId, toStopId,
                hourOfDay, weekend,
                String.format("%.1f", elapsedSec),
                String.format("%.1f", updated.getAvgTravelSeconds()),
                updated.getSampleCount());

        segmentTravelStatsRepository.save(updated)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        err -> log.warn("[GPS_PIPELINE] SEGMENT_TRAVEL_PERSIST_FAILED route={} {}->{} err={}",
                                state.getRouteNumber(), fromStopId, toStopId, err.getMessage())
                );
    }

    private void recordDwellObservation(VehiclePredictionState state, long dwellMs) {
        String stopId = state.getDwellStopId();
        if (stopId == null || state.getRouteNumber() == null) {
            return;
        }
        double dwellSec = dwellMs / 1000.0;
        if (dwellSec < properties.getDwellMinSeconds() || dwellSec > properties.getDwellMaxSeconds()) {
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
        double[] stopFractions = routeGeometryCache.getStopFractions(state.getRouteId(), state.getDirection());
        if (stopFractions == null || stopFractions.length == 0) return -1;
        double nextStopFrac = PredictionMath.findNextStopFraction(stopFractions, fraction);
        if (nextStopFrac < 0) return -1;
        return Math.abs(nextStopFrac - fraction) * totalRouteDistance;
    }

    private double computeDistanceToNextStop(VehiclePredictionState state, double totalRouteDistance) {
        if (state.getRouteNumber() == null || totalRouteDistance <= 0) return -1;
        double[] stopFractions = routeGeometryCache.getStopFractions(state.getRouteId(), state.getDirection());
        if (stopFractions == null || stopFractions.length == 0) return -1;
        double currentFraction = state.getFractionOnRoute();
        double nextStopFraction = PredictionMath.findNextStopFraction(stopFractions, currentFraction);
        if (nextStopFraction < 0) return -1;
        return Math.abs(nextStopFraction - currentFraction) * totalRouteDistance;
    }

    private double computeStopDecelerationFactor(VehiclePredictionState state, double totalRouteDistance) {
        if (state.getRouteNumber() == null) return 1.0;

        double[] stopFractions = routeGeometryCache.getStopFractions(state.getRouteId(), state.getDirection());
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
        double[] stopFractions = routeGeometryCache.getStopFractions(state.getRouteId(), state.getDirection());
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
