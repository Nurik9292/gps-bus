package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.routing.domain.valueobjects.TimePeriod;
import biz.ugur.busroutebackend.routing.infrastructure.config.ETAProperties;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo;
import biz.ugur.busroutebackend.transport.domain.valueobject.SegmentTravelStat;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.DirectVehiclePositionBroadcaster;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage.NextStopEta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class PredictionBroadcaster {

    private static final long HISTORICAL_ETA_MIN_SAMPLES = 3;

    private final DirectVehiclePositionBroadcaster directBroadcaster;
    private final RouteGeometryCache routeGeometryCache;
    private final ETAProperties etaProperties;
    private final PredictionProperties properties;
    private final VehiclePositionPredictor predictor;
    private final biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer pipelineTracer;
    private final java.time.Clock clock;

    private final ConcurrentHashMap<String, double[]> lastBroadcastPosition = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> lastMotionCourse = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> lastBroadcastDirection = new ConcurrentHashMap<>();

    private final LiveFactorSnapshotHolder liveFactorSnapshotHolder;

    public PredictionBroadcaster(DirectVehiclePositionBroadcaster directBroadcaster,
                                  RouteGeometryCache routeGeometryCache,
                                  ETAProperties etaProperties,
                                  PredictionProperties properties,
                                  @Lazy VehiclePositionPredictor predictor,
                                  biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer pipelineTracer,
                                  LiveFactorSnapshotHolder liveFactorSnapshotHolder,
                                  java.time.Clock clock) {
        this.directBroadcaster = directBroadcaster;
        this.routeGeometryCache = routeGeometryCache;
        this.etaProperties = etaProperties;
        this.properties = properties;
        this.predictor = predictor;
        this.pipelineTracer = pipelineTracer;
        this.liveFactorSnapshotHolder = liveFactorSnapshotHolder;
        this.clock = clock;
    }

    public double[] getLastBroadcastPosition(String vehicleId) {
        return lastBroadcastPosition.get(vehicleId);
    }

    public void onVehiclesStaleCleanup(Set<String> activeVehicleIds) {
        lastBroadcastPosition.keySet().retainAll(activeVehicleIds);
        lastMotionCourse.keySet().retainAll(activeVehicleIds);
        lastBroadcastDirection.keySet().retainAll(activeVehicleIds);
    }

    public void resetMotionCourse(String vehicleId) {
        lastMotionCourse.remove(vehicleId);
        lastBroadcastPosition.remove(vehicleId);
        lastBroadcastDirection.remove(vehicleId);
    }

    public static boolean isInColdStart(VehiclePredictionState state) {
        Instant coldStartUntilAt = state.getColdStartUntilAt();
        return coldStartUntilAt != null && coldStartUntilAt.isAfter(Instant.now());
    }

    private java.util.function.Predicate<String> v31LiveRouteSuppressor = r -> false;

    public void v31LiveRouteSuppressor(java.util.function.Predicate<String> suppressor) {
        this.v31LiveRouteSuppressor = suppressor;
    }

    public Mono<Void> broadcast(VehiclePredictionState state) {
        if (state.getRouteNumber() != null
                && v31LiveRouteSuppressor.test(state.getRouteNumber())) {
            pipelineTracer.traceBroadcastSuppressed(state.getVehicleId(),
                    state.getLicensePlate(), "v31-live-cutover");
            return Mono.empty();
        }
        if (state.isInGarage()) {
            pipelineTracer.traceBroadcastSuppressed(state.getVehicleId(), state.getLicensePlate(), "in-garage");
            log.debug("[GPS_PIPELINE] WS_PRED_SUPPRESSED_IN_GARAGE vehicle={} plate={}",
                    state.getVehicleId(), state.getLicensePlate());
            return Mono.empty();
        }

        if (state.getRouteNumber() == null || state.getRouteNumber().isBlank()) {
            pipelineTracer.traceBroadcastSuppressed(state.getVehicleId(), state.getLicensePlate(), "no-route");
            log.debug("[GPS_PIPELINE] WS_PRED_SUPPRESSED_NO_ROUTE vehicle={} plate={} — vehicle not assigned to a route",
                    state.getVehicleId(), state.getLicensePlate());
            return Mono.empty();
        }

        double snapDistance = state.getLastRawToSnapDistanceMeters();
        double hardOffRouteMeters = properties.getHardOffRouteDistanceMeters();
        if (!Double.isNaN(snapDistance) && snapDistance > hardOffRouteMeters) {
            pipelineTracer.traceBroadcastSuppressed(state.getVehicleId(), state.getLicensePlate(),
                    "hard-off-route");
            log.debug("[GPS_PIPELINE] WS_PRED_SUPPRESSED_HARD_OFF_ROUTE vehicle={} plate={} route={} dist={}m threshold={}m",
                    state.getVehicleId(), state.getLicensePlate(), state.getRouteNumber(),
                    String.format("%.0f", snapDistance), String.format("%.0f", hardOffRouteMeters));
            return Mono.empty();
        }

        if (state.isOffRoute()) {
            return broadcastRawGpsFallback(state, "off-route");
        }

        if (isInColdStart(state)) {
            return broadcastRawGpsFallback(state, "cold-start");
        }

        boolean hasRouteGeometry = state.getRouteCoordinates() != null
                && state.getTotalRouteDistanceMeters() > 0;
        boolean hasAnyFraction = state.getFractionOnRoute() >= 0 || state.getLastGpsFraction() >= 0;
        if (!hasRouteGeometry || !hasAnyFraction) {
            pipelineTracer.traceBroadcastSuppressed(state.getVehicleId(), state.getLicensePlate(), "no-route-anchor");
            log.debug("[GPS_PIPELINE] WS_PRED_SUPPRESSED_NO_ANCHOR vehicle={} plate={} route={} hasGeom={} hasFrac={} — awaiting first snap",
                    state.getVehicleId(), state.getLicensePlate(), state.getRouteNumber(),
                    hasRouteGeometry, hasAnyFraction);
            return Mono.empty();
        }

        Integer prevDirection = lastBroadcastDirection.get(state.getVehicleId());
        boolean directionFlipped = prevDirection != null && prevDirection != state.getDirection();
        if (directionFlipped) {
            lastMotionCourse.remove(state.getVehicleId());
            lastBroadcastPosition.remove(state.getVehicleId());
            log.debug("[GPS_PIPELINE] MOTION_COURSE_RESET vehicle={} plate={} reason=direction-flip prev={} new={}",
                    state.getVehicleId(), state.getLicensePlate(), prevDirection, state.getDirection());
        }
        lastBroadcastDirection.put(state.getVehicleId(), state.getDirection());

        double[] prevBroadcast = lastBroadcastPosition.get(state.getVehicleId());
        double broadcastLat = state.getPredictedLatitude();
        double broadcastLon = state.getPredictedLongitude();
        double motionCourseDeg = Double.NaN;
        double distFromPrevBroadcast = Double.NaN;
        if (prevBroadcast != null) {
            double bDelta = biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService
                    .haversineDistanceMeters(prevBroadcast[0], prevBroadcast[1],
                            state.getPredictedLatitude(), state.getPredictedLongitude());
            distFromPrevBroadcast = bDelta;

            double maxStep = properties.getMaxBroadcastStepMeters();
            if (maxStep > 0 && bDelta > maxStep) {
                double easeFactor = maxStep / bDelta;
                broadcastLat = prevBroadcast[0] + easeFactor * (state.getPredictedLatitude() - prevBroadcast[0]);
                broadcastLon = prevBroadcast[1] + easeFactor * (state.getPredictedLongitude() - prevBroadcast[1]);
                log.warn("[GPS_PIPELINE] WS_PRED_GLIDE vehicle={} plate={} jump={}m cappedTo={}m target=({},{}) glided=({},{}) — easing toward target, not teleporting",
                        state.getVehicleId(), state.getLicensePlate(),
                        String.format("%.0f", bDelta), String.format("%.0f", maxStep),
                        String.format("%.5f", state.getPredictedLatitude()),
                        String.format("%.5f", state.getPredictedLongitude()),
                        String.format("%.5f", broadcastLat), String.format("%.5f", broadcastLon));
            }

            if (bDelta >= COURSE_SOURCE_MIN_MOTION_METERS
                    && bDelta <= COURSE_SOURCE_MAX_MOTION_METERS) {
                motionCourseDeg = bearingDegrees(
                        prevBroadcast[0], prevBroadcast[1],
                        state.getPredictedLatitude(), state.getPredictedLongitude());
            } else if (bDelta > COURSE_SOURCE_MAX_MOTION_METERS) {
                lastMotionCourse.remove(state.getVehicleId());
            }
        }
        lastBroadcastPosition.put(state.getVehicleId(),
                new double[]{broadcastLat, broadcastLon});

        Double fractionValue = (state.getFractionOnRoute() >= 0) ? state.getFractionOnRoute() : null;
        List<NextStopEta> nextStops = computeNextStopsEta(state, 3);

        long msSinceGpsForBroadcast = state.getLastReceivedAt() != null
                ? Instant.now().toEpochMilli() - state.getLastReceivedAt().toEpochMilli()
                : Long.MAX_VALUE;
        boolean freshGps = msSinceGpsForBroadcast < properties.getFreshGpsWindowMs();
        double broadcastSpeedKmh = freshGps ? state.getRawGpsSpeedKmh() : state.getSpeedKmh();
        boolean broadcastInMotion = freshGps
                ? state.getRawGpsSpeedKmh() >= properties.getMinSpeedKmh()
                : state.isInMotion();

        if (state.getRawGpsSpeedKmh() < STATIONARY_RAW_SPEED_THRESHOLD_KMH
                && !state.isInMotion()) {
            if (state.getSpeedKmh() > STATIONARY_OVERRIDE_LOG_THRESHOLD_KMH) {
                log.debug("[GPS_PIPELINE] STATIONARY_BROADCAST_OVERRIDE vehicle={} plate={} kalmanSpeed={}km/h -> 0 (rawSpeed={}km/h moving={})",
                        state.getVehicleId(), state.getLicensePlate(),
                        String.format("%.1f", state.getSpeedKmh()),
                        String.format("%.1f", state.getRawGpsSpeedKmh()),
                        state.isInMotion());
            }
            broadcastSpeedKmh = 0.0;
            broadcastInMotion = false;
        }

        double broadcastCourse = resolveBroadcastCourse(
                state, motionCourseDeg, broadcastSpeedKmh);

        Integer broadcastDirection = state.isDirectionConfirmed() ? state.getDirection() : null;
        Boolean broadcastLine = broadcastDirection == null ? null : (broadcastDirection == 0);

        VehiclePositionWebSocketMessage msg = new VehiclePositionWebSocketMessage(
                state.getVehicleId(),
                state.getLicensePlate(),
                state.getRouteNumber(),
                broadcastLat,
                broadcastLon,
                broadcastSpeedKmh,
                broadcastInMotion,
                LocalDateTime.now(),
                broadcastCourse,
                broadcastLine,
                nextStops.isEmpty() ? null : nextStops,
                Boolean.TRUE,
                fractionValue,
                PredictionMath.computeConfidence(state.getLastReceivedAt(), state.getFractionOnRoute(), Instant.now()).name(),
                broadcastDirection
        );

        final double motionCourseFinal = motionCourseDeg;
        final double distFromPrevBroadcastFinal = distFromPrevBroadcast;
        final double broadcastCourseFinal = broadcastCourse;
        final double broadcastSpeedKmhFinal = broadcastSpeedKmh;
        final boolean broadcastInMotionFinal = broadcastInMotion;
        return Mono.fromRunnable(() -> {
            try {
                directBroadcaster.broadcastDirect(msg);
                pipelineTracer.traceWsBroadcast(
                        state.getVehicleId(), state.getLicensePlate(),
                        state.getPredictedLatitude(), state.getPredictedLongitude(),
                        broadcastSpeedKmhFinal, broadcastInMotionFinal,
                        Boolean.TRUE,
                        fractionValue != null ? "SNAPPED" : "DEAD_RECKONING");
                pipelineTracer.traceWsPayload(
                        state.getVehicleId(), state.getLicensePlate(), state.getRouteNumber(),
                        msg.getLatitude(), msg.getLongitude(),
                        msg.getCourse(), msg.getDirection(),
                        msg.getSpeedKmh(), msg.getIsInMotion(),
                        msg.getPredicted(), msg.getConfidence(),
                        prevBroadcast != null ? prevBroadcast[0] : Double.NaN,
                        prevBroadcast != null ? prevBroadcast[1] : Double.NaN,
                        motionCourseFinal, state.getCourse());
                log.debug("[GPS_PIPELINE] WS_PRED vehicle={} plate={} mode={} frac={} lat={} lon={} speed={}km/h rawSpeed={}km/h moving={} course={}° eta_stops={}",
                        state.getVehicleId(), state.getLicensePlate(),
                        fractionValue != null ? "SNAPPED" : "DEAD_RECKONING",
                        fractionValue != null ? String.format("%.4f", fractionValue) : "-",
                        String.format("%.6f", state.getPredictedLatitude()),
                        String.format("%.6f", state.getPredictedLongitude()),
                        String.format("%.1f", broadcastSpeedKmhFinal),
                        String.format("%.1f", state.getRawGpsSpeedKmh()),
                        broadcastInMotionFinal,
                        String.format("%.1f", broadcastCourseFinal),
                        nextStops.size());

                if (!Double.isNaN(motionCourseFinal)
                        && broadcastSpeedKmhFinal >= COURSE_SOURCE_MIN_SPEED_KMH) {
                    double routeCourse = state.getCourse();
                    double delta = angularDelta(routeCourse, motionCourseFinal);
                    if (delta >= COURSE_SOURCE_ALERT_DELTA_DEG) {
                        log.info("[GPS_PIPELINE] COURSE_SOURCE_DIVERGENCE vehicle={} plate={} route={} dir={} " +
                                        "routeCourse={}° motionCourse={}° delta={}° broadcastCourse={}° " +
                                        "distFromPrev={}m speed={}km/h frac={}",
                                state.getVehicleId(), state.getLicensePlate(), state.getRouteNumber(),
                                state.getDirection(),
                                String.format("%.1f", routeCourse),
                                String.format("%.1f", motionCourseFinal),
                                String.format("%.1f", delta),
                                String.format("%.1f", broadcastCourseFinal),
                                String.format("%.1f", distFromPrevBroadcastFinal),
                                String.format("%.1f", broadcastSpeedKmhFinal),
                                fractionValue != null ? String.format("%.4f", fractionValue) : "-");
                    }
                }
            } catch (Exception e) {
                log.warn("[GPS_PIPELINE] BROADCAST_FAILED vehicle={}: {}", state.getVehicleId(), e.getMessage());
            }
        });
    }

    private static final double DEAD_RECKONING_MAX_AGE_SEC = 30.0;
    private static final double DEAD_RECKONING_MIN_SPEED_KMH = 1.0;
    private static final double EARTH_METERS_PER_DEG_LAT = 111320.0;

    private static final double COURSE_SOURCE_MIN_MOTION_METERS = 5.0;
    private static final double COURSE_SOURCE_MIN_SPEED_KMH = 2.0;
    private static final double COURSE_SOURCE_ALERT_DELTA_DEG = 30.0;
    private static final double COURSE_SOURCE_MAX_MOTION_METERS = 200.0;

    private static final double STATIONARY_RAW_SPEED_THRESHOLD_KMH = 1.0;
    private static final double STATIONARY_OVERRIDE_LOG_THRESHOLD_KMH = 5.0;

    private Mono<Void> broadcastRawGpsFallback(VehiclePredictionState state, String reason) {
        double baseLat = state.getGpsLatitude();
        double baseLon = state.getGpsLongitude();
        if (baseLat == 0.0 && baseLon == 0.0) {
            pipelineTracer.traceBroadcastSuppressed(state.getVehicleId(), state.getLicensePlate(),
                    reason + "-no-raw-gps");
            log.warn("[GPS_PIPELINE] WS_RAW_GPS_SUPPRESSED vehicle={} plate={} reason={} — raw GPS not set (gpsLatitude/gpsLongitude both 0)",
                    state.getVehicleId(), state.getLicensePlate(), reason);
            return Mono.empty();
        }

        double speedKmh = state.getRawGpsSpeedKmh();
        double course = state.getCourse();
        double[] extrapolated = extrapolateDeadReckoning(
                baseLat, baseLon, speedKmh, course, state.getLastReceivedAt());
        double lat = extrapolated[0];
        double lon = extrapolated[1];

        boolean broadcastInMotion = speedKmh >= properties.getMinSpeedKmh();

        Integer fallbackDirection = state.isDirectionConfirmed() ? state.getDirection() : null;
        Boolean fallbackLine = fallbackDirection == null ? null : (fallbackDirection == 0);

        VehiclePositionWebSocketMessage msg = new VehiclePositionWebSocketMessage(
                state.getVehicleId(),
                state.getLicensePlate(),
                state.getRouteNumber(),
                lat,
                lon,
                speedKmh,
                broadcastInMotion,
                LocalDateTime.now(),
                course,
                fallbackLine,
                null,
                Boolean.FALSE,
                null,
                "RAW_GPS",
                fallbackDirection
        );

        return Mono.fromRunnable(() -> {
            try {
                directBroadcaster.broadcastDirect(msg);
                pipelineTracer.traceWsBroadcast(
                        state.getVehicleId(), state.getLicensePlate(),
                        lat, lon,
                        speedKmh, broadcastInMotion,
                        Boolean.FALSE, "RAW_GPS_FALLBACK");
                pipelineTracer.traceWsPayload(
                        state.getVehicleId(), state.getLicensePlate(), state.getRouteNumber(),
                        msg.getLatitude(), msg.getLongitude(),
                        msg.getCourse(), msg.getDirection(),
                        msg.getSpeedKmh(), msg.getIsInMotion(),
                        msg.getPredicted(), msg.getConfidence(),
                        Double.NaN, Double.NaN, Double.NaN, state.getCourse());
                log.warn("[GPS_PIPELINE] WS_RAW_GPS_FALLBACK vehicle={} plate={} reason={} lat={} lon={} speed={}km/h moving={}",
                        state.getVehicleId(), state.getLicensePlate(), reason,
                        String.format("%.6f", lat),
                        String.format("%.6f", lon),
                        String.format("%.1f", speedKmh),
                        broadcastInMotion);
            } catch (Exception e) {
                log.warn("[GPS_PIPELINE] WS_RAW_GPS_BROADCAST_FAILED vehicle={}: {}",
                        state.getVehicleId(), e.getMessage());
            }
        });
    }

    private double resolveBroadcastCourse(VehiclePredictionState state,
                                           double motionCourseDeg,
                                           double speedKmh) {
        if (!Double.isNaN(motionCourseDeg) && speedKmh >= COURSE_SOURCE_MIN_SPEED_KMH) {
            lastMotionCourse.put(state.getVehicleId(), motionCourseDeg);
            return motionCourseDeg;
        }
        Double sticky = lastMotionCourse.get(state.getVehicleId());
        if (sticky != null) {
            return sticky;
        }
        return state.getCourse();
    }

    private static double bearingDegrees(double lat1, double lon1, double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        double rlat1 = Math.toRadians(lat1);
        double rlat2 = Math.toRadians(lat2);
        double y = Math.sin(dLon) * Math.cos(rlat2);
        double x = Math.cos(rlat1) * Math.sin(rlat2)
                 - Math.sin(rlat1) * Math.cos(rlat2) * Math.cos(dLon);
        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360.0) % 360.0;
    }

    private static double angularDelta(double a, double b) {
        double d = Math.abs(a - b) % 360.0;
        return d > 180.0 ? 360.0 - d : d;
    }

    private double[] extrapolateDeadReckoning(double baseLat, double baseLon,
                                              double speedKmh, double courseDeg,
                                              Instant lastReceivedAt) {
        if (lastReceivedAt == null || speedKmh < DEAD_RECKONING_MIN_SPEED_KMH) {
            return new double[]{baseLat, baseLon};
        }
        double ageSec = (clock.instant().toEpochMilli() - lastReceivedAt.toEpochMilli()) / 1000.0;
        if (ageSec <= 0 || ageSec > DEAD_RECKONING_MAX_AGE_SEC) {
            return new double[]{baseLat, baseLon};
        }
        double distMeters = (speedKmh / 3.6) * ageSec;
        double courseRad = Math.toRadians(courseDeg);
        double dLat = (distMeters * Math.cos(courseRad)) / EARTH_METERS_PER_DEG_LAT;
        double metersPerDegLon = EARTH_METERS_PER_DEG_LAT * Math.cos(Math.toRadians(baseLat));
        double dLon = metersPerDegLon > 0
                ? (distMeters * Math.sin(courseRad)) / metersPerDegLon
                : 0.0;
        return new double[]{baseLat + dLat, baseLon + dLon};
    }

        private static final int LIVE_FACTOR_HORIZON_SEGMENTS = 5;

    private static double horizonDampedLiveFactor(double factor, int segmentIndexAhead) {
        if (segmentIndexAhead >= LIVE_FACTOR_HORIZON_SEGMENTS) {
            return 1.0;
        }
        double weight = (LIVE_FACTOR_HORIZON_SEGMENTS - segmentIndexAhead)
                / (double) LIVE_FACTOR_HORIZON_SEGMENTS;
        return 1.0 + (factor - 1.0) * weight;
    }

    private List<NextStopEta> computeNextStopsEta(VehiclePredictionState state, int maxStops) {
        if (!state.isDirectionConfirmed()) {
            return List.of();
        }
        double trueFraction = state.getLastGpsFraction() >= 0
                ? state.getLastGpsFraction()
                : state.getFractionOnRoute();
        if (trueFraction < 0 || state.getTotalRouteDistanceMeters() <= 0
                || state.getRouteNumber() == null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ashgabat"));
        TimePeriod period = TimePeriod.fromDateTime(now);
        double speedKmh = state.getSmoothedSpeedKmh() > 0
                ? state.getSmoothedSpeedKmh()
                : state.getRawGpsSpeedKmh();
        if (speedKmh < etaProperties.getSpeed().getMovingThresholdKmh()) {
            speedKmh = period.getAverageSpeedKmh();
        }
        double effectiveSpeed = speedKmh;
        double totalDist = state.getTotalRouteDistanceMeters();
        double trafficMult = period.getTrafficMultiplier(TimePeriod.isWeekend(now));
        int hourOfDay = now.getHour();
        boolean weekend = now.getDayOfWeek() == DayOfWeek.SATURDAY
                || now.getDayOfWeek() == DayOfWeek.SUNDAY;

        List<RouteStopInfo> stopsAhead = routeGeometryCache
                .getStopsAhead(state.getRouteNumber(), state.getDirection(), trueFraction)
                .stream()
                .limit(maxStops)
                .toList();
        if (stopsAhead.isEmpty()) {
            return List.of();
        }

        List<NextStopEta> etas = new ArrayList<>(stopsAhead.size());
        double cumulativeSeconds = 0.0;
        double prevStopFrac = trueFraction;
        String prevStopId = null;

        for (RouteStopInfo stop : stopsAhead) {
            double stopFrac = stop.getDistanceFromStartMeters() / totalDist;
            double distMeters = (stopFrac - prevStopFrac) * totalDist;

            double segmentSeconds;
            SegmentTravelStat historical = prevStopId != null
                    ? predictor.getSegmentTravelStat(state.getRouteNumber(), state.getDirection(),
                            prevStopId, stop.getStopId(), hourOfDay, weekend)
                    : null;
            biz.ugur.busroutebackend.prediction.core.history.SegmentDwellHistory.Stat sharedEdge =
                    prevStopId != null
                            ? liveFactorSnapshotHolder.edgeBaseline(prevStopId, stop.getStopId())
                            : null;
            if (historical != null && historical.getSampleCount() >= HISTORICAL_ETA_MIN_SAMPLES) {
                segmentSeconds = historical.getAvgTravelSeconds();
            } else if (sharedEdge != null && sharedEdge.n() >= HISTORICAL_ETA_MIN_SAMPLES) {
                segmentSeconds = sharedEdge.meanSec();
            } else {
                segmentSeconds = (distMeters / 1000.0 / effectiveSpeed) * 3600.0 * trafficMult;
            }
            if (prevStopId != null) {
                segmentSeconds *= horizonDampedLiveFactor(
                        liveFactorSnapshotHolder.factor(prevStopId, stop.getStopId()),
                        etas.size());
            }

            cumulativeSeconds += segmentSeconds;
            int etaMin = (int) Math.max(1, Math.ceil(cumulativeSeconds / 60.0));
            int distFromVehicleMeters = (int) ((stopFrac - trueFraction) * totalDist);
            etas.add(new NextStopEta(stop.getStopId(), stop.getStopName(), etaMin, distFromVehicleMeters));

            prevStopFrac = stopFrac;
            prevStopId = stop.getStopId();
        }
        return etas;
    }
}
