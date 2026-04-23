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

        double[] prevBroadcast = lastBroadcastPosition.get(state.getVehicleId());
        if (prevBroadcast != null) {
            double bDelta = biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService
                    .haversineDistanceMeters(prevBroadcast[0], prevBroadcast[1],
                            state.getPredictedLatitude(), state.getPredictedLongitude());
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

        VehiclePositionWebSocketMessage msg = new VehiclePositionWebSocketMessage(
                state.getVehicleId(),
                state.getLicensePlate(),
                state.getRouteNumber(),
                state.getPredictedLatitude(),
                state.getPredictedLongitude(),
                broadcastSpeedKmh,
                broadcastInMotion,
                LocalDateTime.now(),
                state.getCourse(),
                state.getDirection() == 0,
                nextStops.isEmpty() ? null : nextStops,
                Boolean.TRUE,
                fractionValue,
                PredictionMath.computeConfidence(state.getLastReceivedAt(), state.getFractionOnRoute(), Instant.now()).name()
        );

        return Mono.fromRunnable(() -> {
            try {
                directBroadcaster.broadcastDirect(msg);
                pipelineTracer.traceWsBroadcast(
                        state.getVehicleId(), state.getLicensePlate(),
                        state.getPredictedLatitude(), state.getPredictedLongitude(),
                        broadcastSpeedKmh, broadcastInMotion,
                        Boolean.TRUE,
                        fractionValue != null ? "SNAPPED" : "DEAD_RECKONING");
                log.debug("[GPS_PIPELINE] WS_PRED vehicle={} plate={} mode={} frac={} lat={} lon={} speed={}km/h rawSpeed={}km/h moving={} eta_stops={}",
                        state.getVehicleId(), state.getLicensePlate(),
                        fractionValue != null ? "SNAPPED" : "DEAD_RECKONING",
                        fractionValue != null ? String.format("%.4f", fractionValue) : "-",
                        String.format("%.6f", state.getPredictedLatitude()),
                        String.format("%.6f", state.getPredictedLongitude()),
                        String.format("%.1f", broadcastSpeedKmh),
                        String.format("%.1f", state.getRawGpsSpeedKmh()),
                        broadcastInMotion,
                        nextStops.size());
            } catch (Exception e) {
                log.warn("Failed to broadcast prediction for vehicle {}: {}", state.getVehicleId(), e.getMessage());
            }
        });
    }

    private Mono<Void> broadcastRawGpsFallback(VehiclePredictionState state, String reason) {
        double lat = state.getGpsLatitude();
        double lon = state.getGpsLongitude();
        if (lat == 0.0 && lon == 0.0) {
            pipelineTracer.traceBroadcastSuppressed(state.getVehicleId(), state.getLicensePlate(),
                    reason + "-no-raw-gps");
            log.warn("[GPS_PIPELINE] WS_RAW_GPS_SUPPRESSED vehicle={} plate={} reason={} — raw GPS not set (gpsLatitude/gpsLongitude both 0)",
                    state.getVehicleId(), state.getLicensePlate(), reason);
            return Mono.empty();
        }

        double broadcastSpeedKmh = state.getRawGpsSpeedKmh();
        boolean broadcastInMotion = broadcastSpeedKmh >= properties.getMinSpeedKmh();

        VehiclePositionWebSocketMessage msg = new VehiclePositionWebSocketMessage(
                state.getVehicleId(),
                state.getLicensePlate(),
                state.getRouteNumber(),
                lat,
                lon,
                broadcastSpeedKmh,
                broadcastInMotion,
                LocalDateTime.now(),
                state.getCourse(),
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
                        broadcastSpeedKmh, broadcastInMotion,
                        Boolean.FALSE, "RAW_GPS_FALLBACK");
                log.warn("[GPS_PIPELINE] WS_RAW_GPS_FALLBACK vehicle={} plate={} reason={} lat={} lon={} speed={}km/h moving={}",
                        state.getVehicleId(), state.getLicensePlate(), reason,
                        String.format("%.6f", lat),
                        String.format("%.6f", lon),
                        String.format("%.1f", broadcastSpeedKmh),
                        broadcastInMotion);
            } catch (Exception e) {
                log.warn("Failed to broadcast raw GPS fallback for vehicle {}: {}",
                        state.getVehicleId(), e.getMessage());
            }
        });
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
