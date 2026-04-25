package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.routing.domain.valueobjects.TimePeriod;
import biz.ugur.busroutebackend.routing.infrastructure.config.ETAProperties;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.DirectVehiclePositionBroadcaster;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage.NextStopEta;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class PredictionBroadcaster {

    private final DirectVehiclePositionBroadcaster directBroadcaster;
    private final RouteGeometryCache routeGeometryCache;
    private final ETAProperties etaProperties;
    private final PredictionProperties properties;
    private final biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer pipelineTracer;

    private final ConcurrentHashMap<String, double[]> lastBroadcastPosition = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double> lastMotionCourse = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> lastBroadcastDirection = new ConcurrentHashMap<>();

    public PredictionBroadcaster(DirectVehiclePositionBroadcaster directBroadcaster,
                                  RouteGeometryCache routeGeometryCache,
                                  ETAProperties etaProperties,
                                  PredictionProperties properties,
                                  biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer pipelineTracer) {
        this.directBroadcaster = directBroadcaster;
        this.routeGeometryCache = routeGeometryCache;
        this.etaProperties = etaProperties;
        this.properties = properties;
        this.pipelineTracer = pipelineTracer;
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

    public Mono<Void> broadcast(VehiclePredictionState state) {
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
        double motionCourseDeg = Double.NaN;
        double distFromPrevBroadcast = Double.NaN;
        if (prevBroadcast != null) {
            double bDelta = biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService
                    .haversineDistanceMeters(prevBroadcast[0], prevBroadcast[1],
                            state.getPredictedLatitude(), state.getPredictedLongitude());
            distFromPrevBroadcast = bDelta;
            if (bDelta > 500.0) {
                log.warn("[GPS_PIPELINE] WS_PRED_BROADCAST_JUMP vehicle={} plate={} delta={}m prev=({},{}) new=({},{}) coldStart={}",
                        state.getVehicleId(), state.getLicensePlate(),
                        String.format("%.0f", bDelta),
                        String.format("%.5f", prevBroadcast[0]),
                        String.format("%.5f", prevBroadcast[1]),
                        String.format("%.5f", state.getPredictedLatitude()),
                        String.format("%.5f", state.getPredictedLongitude()),
                        isInColdStart(state));
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
                new double[]{state.getPredictedLatitude(), state.getPredictedLongitude()});

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

        double broadcastCourse = resolveBroadcastCourse(
                state, motionCourseDeg, broadcastSpeedKmh);

        VehiclePositionWebSocketMessage msg = new VehiclePositionWebSocketMessage(
                state.getVehicleId(),
                state.getLicensePlate(),
                state.getRouteNumber(),
                state.getPredictedLatitude(),
                state.getPredictedLongitude(),
                broadcastSpeedKmh,
                broadcastInMotion,
                LocalDateTime.now(),
                broadcastCourse,
                state.getDirection() == 0,
                nextStops.isEmpty() ? null : nextStops,
                Boolean.TRUE,
                fractionValue,
                PredictionMath.computeConfidence(state.getLastReceivedAt(), state.getFractionOnRoute(), Instant.now()).name()
        );

        final double motionCourseFinal = motionCourseDeg;
        final double distFromPrevBroadcastFinal = distFromPrevBroadcast;
        final double broadcastCourseFinal = broadcastCourse;
        return Mono.fromRunnable(() -> {
            try {
                directBroadcaster.broadcastDirect(msg);
                pipelineTracer.traceWsBroadcast(
                        state.getVehicleId(), state.getLicensePlate(),
                        state.getPredictedLatitude(), state.getPredictedLongitude(),
                        broadcastSpeedKmh, broadcastInMotion,
                        Boolean.TRUE,
                        fractionValue != null ? "SNAPPED" : "DEAD_RECKONING");
                log.debug("[GPS_PIPELINE] WS_PRED vehicle={} plate={} mode={} frac={} lat={} lon={} speed={}km/h rawSpeed={}km/h moving={} course={}° eta_stops={}",
                        state.getVehicleId(), state.getLicensePlate(),
                        fractionValue != null ? "SNAPPED" : "DEAD_RECKONING",
                        fractionValue != null ? String.format("%.4f", fractionValue) : "-",
                        String.format("%.6f", state.getPredictedLatitude()),
                        String.format("%.6f", state.getPredictedLongitude()),
                        String.format("%.1f", broadcastSpeedKmh),
                        String.format("%.1f", state.getRawGpsSpeedKmh()),
                        broadcastInMotion,
                        String.format("%.1f", broadcastCourseFinal),
                        nextStops.size());

                if (!Double.isNaN(motionCourseFinal)
                        && broadcastSpeedKmh >= COURSE_SOURCE_MIN_SPEED_KMH) {
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
                                String.format("%.1f", broadcastSpeedKmh),
                                fractionValue != null ? String.format("%.4f", fractionValue) : "-");
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to broadcast prediction for vehicle {}: {}", state.getVehicleId(), e.getMessage());
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
                state.getDirection() == 0,
                null,
                Boolean.FALSE,
                null,
                "RAW_GPS"
        );

        return Mono.fromRunnable(() -> {
            try {
                directBroadcaster.broadcastDirect(msg);
                pipelineTracer.traceWsBroadcast(
                        state.getVehicleId(), state.getLicensePlate(),
                        lat, lon,
                        speedKmh, broadcastInMotion,
                        Boolean.FALSE, "RAW_GPS_FALLBACK");
                log.warn("[GPS_PIPELINE] WS_RAW_GPS_FALLBACK vehicle={} plate={} reason={} lat={} lon={} speed={}km/h moving={}",
                        state.getVehicleId(), state.getLicensePlate(), reason,
                        String.format("%.6f", lat),
                        String.format("%.6f", lon),
                        String.format("%.1f", speedKmh),
                        broadcastInMotion);
            } catch (Exception e) {
                log.warn("Failed to broadcast raw GPS fallback for vehicle {}: {}",
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

    private static double[] extrapolateDeadReckoning(double baseLat, double baseLon,
                                                     double speedKmh, double courseDeg,
                                                     Instant lastReceivedAt) {
        if (lastReceivedAt == null || speedKmh < DEAD_RECKONING_MIN_SPEED_KMH) {
            return new double[]{baseLat, baseLon};
        }
        double ageSec = (Instant.now().toEpochMilli() - lastReceivedAt.toEpochMilli()) / 1000.0;
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

    private List<NextStopEta> computeNextStopsEta(VehiclePredictionState state, int maxStops) {
        double trueFraction = state.getLastGpsFraction() >= 0
                ? state.getLastGpsFraction()
                : state.getFractionOnRoute();
        if (trueFraction < 0 || state.getTotalRouteDistanceMeters() <= 0
                || state.getRouteNumber() == null) {
            return List.of();
        }
        LocalDateTime now = LocalDateTime.now();
        TimePeriod period = TimePeriod.fromDateTime(now);
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
}
