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

        if (state.getFractionOnRoute() < 0
                && state.getRouteNumber() != null
                && !state.getRouteNumber().isBlank()) {
            pipelineTracer.traceBroadcastSuppressed(state.getVehicleId(), state.getLicensePlate(), "unsnapped-awaiting-gps");
            log.debug("[GPS_PIPELINE] WS_PRED_SUPPRESSED_UNSNAPPED vehicle={} plate={} route={} — state awaiting fresh GPS to re-snap",
                    state.getVehicleId(), state.getLicensePlate(), state.getRouteNumber());
            return Mono.empty();
        }

        if (state.isOffRoute()) {
            pipelineTracer.traceBroadcastSuppressed(state.getVehicleId(), state.getLicensePlate(), "off-route");
            log.debug("[GPS_PIPELINE] WS_PRED_SUPPRESSED_OFF_ROUTE vehicle={} plate={} — vehicle {}+ GPS points away from route",
                    state.getVehicleId(), state.getLicensePlate(), state.getConsecutiveOffRouteCount());
            return Mono.empty();
        }

        if (!state.isInMotion() && state.getSpeedKmh() == 0
                && (state.getRouteNumber() == null || state.getRouteNumber().isBlank())) {
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

        if (isInColdStart(state)) {
            pipelineTracer.traceBroadcastSuppressed(state.getVehicleId(), state.getLicensePlate(), "cold-start");
            log.debug("[GPS_PIPELINE] WS_PRED_SUPPRESSED_COLD_START vehicle={} plate={} — state stabilizing",
                    state.getVehicleId(), state.getLicensePlate());
            return Mono.empty();
        }

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
                fractionValue,
                PredictionMath.computeConfidence(state.getLastReceivedAt(), state.getFractionOnRoute(), Instant.now()).name()
        );

        return Mono.fromRunnable(() -> {
            try {
                directBroadcaster.broadcastDirect(msg);
                pipelineTracer.traceWsBroadcast(
                        state.getVehicleId(), state.getLicensePlate(),
                        state.getPredictedLatitude(), state.getPredictedLongitude(),
                        state.getSpeedKmh(), state.isInMotion(),
                        Boolean.TRUE,
                        fractionValue != null ? "SNAPPED" : "DEAD_RECKONING");
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
