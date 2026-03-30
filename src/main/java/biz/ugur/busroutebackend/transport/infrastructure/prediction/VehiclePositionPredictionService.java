package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dead reckoning prediction service (Phase 1 + Phase 2).
 *
 * <p>Phase 1 — dead reckoning by speed + course (plain direction vector).
 * <p>Phase 2 — route-aware prediction: when a vehicle is assigned to a route and its
 * geometry is cached, the predicted position advances along the route geometry using a
 * fraction [0.0–1.0], producing smooth road-following movement.
 */
@Service
@Slf4j
public class VehiclePositionPredictionService {

    /** Metres per degree of latitude (approximately constant) */
    private static final double METRES_PER_DEGREE_LAT = 111_320.0;

    /** Prediction step size in seconds (matches scheduler interval) */
    private static final double DT_SECONDS = 1.0;

    private final ConcurrentHashMap<String, VehiclePredictionState> vehicleStates = new ConcurrentHashMap<>();

    private final PredictionProperties properties;
    private final VehiclePositionWebSocketPublisher webSocketPublisher;
    private final RouteGeometryCache routeGeometryCache;
    private final MapMatchingService mapMatchingService;

    public VehiclePositionPredictionService(PredictionProperties properties,
                                             VehiclePositionWebSocketPublisher webSocketPublisher,
                                             RouteGeometryCache routeGeometryCache,
                                             MapMatchingService mapMatchingService) {
        this.properties = properties;
        this.webSocketPublisher = webSocketPublisher;
        this.routeGeometryCache = routeGeometryCache;
        this.mapMatchingService = mapMatchingService;
    }

    /**
     * Called by {@link biz.ugur.busroutebackend.transport.application.usecase.UpdateVehiclePositionsUseCase}
     * whenever a real GPS fix arrives for a vehicle.
     *
     * @param direction 0 = forward, 1 = backward (pass current vehicle direction)
     */
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

        VehiclePredictionState existing = vehicleStates.get(vehicleId);

        // Blend existing predicted position toward the new real position
        double predictedLat = latitude;
        double predictedLon = longitude;
        if (existing != null) {
            double cf = properties.getCorrectionFactor();
            predictedLat = existing.getPredictedLatitude() + cf * (latitude - existing.getPredictedLatitude());
            predictedLon = existing.getPredictedLongitude() + cf * (longitude - existing.getPredictedLongitude());
        }

        // ---- Phase 2: try to snap to route and obtain route geometry ----
        List<double[]> routeCoords = null;
        double totalDist = 0;
        double fraction = -1;

        if (routeNumber != null && properties.isSnapToRoute()) {
            routeCoords = routeGeometryCache.getPoints(routeNumber, direction);
            if (routeCoords == null) {
                // Try opposite direction
                int opposite = (direction == 0) ? 1 : 0;
                routeCoords = routeGeometryCache.getPoints(routeNumber, opposite);
                if (routeCoords != null) {
                    direction = opposite;
                }
            }

            if (routeCoords != null) {
                totalDist = routeGeometryCache.getTotalDistance(routeNumber, direction);
                MapMatchingService.SnappedResult snap =
                        mapMatchingService.snapToNearestSegment(latitude, longitude, routeCoords);
                if (snap.snapped()) {
                    predictedLat = snap.latitude();
                    predictedLon = snap.longitude();
                    fraction = snap.fraction();
                    // Derive course from route geometry for snapped vehicles
                    course = mapMatchingService.calculateCourseFromRoute(routeCoords, fraction, direction);
                }
            }
        }

        VehiclePredictionState state = VehiclePredictionState.builder()
                .vehicleId(vehicleId)
                .licensePlate(licensePlate)
                .routeNumber(routeNumber)
                .gpsLatitude(latitude)
                .gpsLongitude(longitude)
                .speedKmh(speedKmh)
                .course(course)
                .inMotion(inMotion)
                .lastGpsUpdate(timestamp)
                .predictedLatitude(predictedLat)
                .predictedLongitude(predictedLon)
                .routeCoordinates(routeCoords)
                .totalRouteDistanceMeters(totalDist)
                .fractionOnRoute(fraction)
                .direction(direction)
                .build();

        vehicleStates.put(vehicleId, state);
        log.trace("GPS update: vehicleId={}, lat={}, lon={}, speed={}km/h, fraction={}",
                vehicleId, latitude, longitude, speedKmh, fraction);
    }

    /**
     * Called every second by {@link PositionPredictionScheduler}.
     * Advances each active vehicle's predicted position and broadcasts via WebSocket.
     * Also triggers periodic cleanup of stale states.
     */
    public Mono<Void> predictNextPositions() {
        if (!properties.isEnabled()) {
            return Mono.empty();
        }

        cleanupStaleStates();

        Instant now = Instant.now();
        long maxAgeMs = properties.getMaxAgeMs();

        List<VehiclePredictionState> activeStates = vehicleStates.values().stream()
                .filter(state -> {
                    long ageMs = now.toEpochMilli() - state.getLastGpsUpdate().toEpochMilli();
                    return ageMs <= maxAgeMs;
                })
                .filter(state -> !isAtRouteBoundary(state))
                .toList();

        if (activeStates.isEmpty()) {
            return Mono.empty();
        }

        log.trace("Prediction cycle: {} active vehicles", activeStates.size());

        return Flux.fromIterable(activeStates)
                .flatMap(state -> {
                    VehiclePredictionState advanced = advanceState(state);
                    vehicleStates.put(advanced.getVehicleId(), advanced);
                    return broadcastPrediction(advanced);
                })
                .then();
    }

    /**
     * Removes entries from {@code vehicleStates} that have not received a GPS update
     * in more than 5 minutes. Prevents unbounded memory growth when vehicles go offline.
     */
    private void cleanupStaleStates() {
        Instant cutoff = Instant.now().minusSeconds(300);
        vehicleStates.entrySet().removeIf(e -> e.getValue().getLastGpsUpdate().isBefore(cutoff));
    }

    /**
     * Returns {@code true} when a route-aware vehicle has reached the end (or start) of its route
     * and should no longer generate predictions.
     */
    private boolean isAtRouteBoundary(VehiclePredictionState state) {
        if (state.getRouteCoordinates() == null || state.getFractionOnRoute() < 0) {
            return false; // plain dead reckoning — no boundary
        }
        double f = state.getFractionOnRoute();
        return (state.getDirection() == 0 && f >= 1.0)
                || (state.getDirection() == 1 && f <= 0.0);
    }

    // ---- private ----

    /**
     * Applies one prediction step.
     * Phase 2: uses route-aware fraction movement when geometry is available.
     * Phase 1 fallback: dead reckoning by speed + course vector.
     */
    private VehiclePredictionState advanceState(VehiclePredictionState state) {
        if (!state.isInMotion() || state.getSpeedKmh() < properties.getMinSpeedKmh()) {
            return state;
        }

        double decayedSpeedKmh = state.getSpeedKmh() * properties.getDecayFactor();

        // ---- Phase 2: route-aware prediction ----
        List<double[]> routeCoords = state.getRouteCoordinates();
        if (routeCoords != null && state.getFractionOnRoute() >= 0
                && state.getTotalRouteDistanceMeters() > 0) {

            double speedMs = decayedSpeedKmh / 3.6;
            double fractionDelta = speedMs * DT_SECONDS / state.getTotalRouteDistanceMeters();

            double newFraction;
            if (state.getDirection() == 0) {
                newFraction = Math.min(state.getFractionOnRoute() + fractionDelta, 1.0);
            } else {
                newFraction = Math.max(state.getFractionOnRoute() - fractionDelta, 0.0);
            }

            double[] coords = mapMatchingService.interpolateRoutePoint(routeCoords, newFraction);
            if (coords == null) return state;

            double newCourse = mapMatchingService.calculateCourseFromRoute(
                    routeCoords, newFraction, state.getDirection());

            return state.toBuilder()
                    .speedKmh(decayedSpeedKmh)
                    .predictedLatitude(coords[0])
                    .predictedLongitude(coords[1])
                    .fractionOnRoute(newFraction)
                    .course(newCourse)
                    .build();
        }

        // ---- Phase 1 fallback: dead reckoning by speed + course ----
        double speedMs   = decayedSpeedKmh / 3.6;
        double courseRad = Math.toRadians(state.getCourse());

        double dNorth = speedMs * DT_SECONDS * Math.cos(courseRad);
        double dEast  = speedMs * DT_SECONDS * Math.sin(courseRad);

        double dLat = dNorth / METRES_PER_DEGREE_LAT;
        double dLon = dEast  / (METRES_PER_DEGREE_LAT * Math.cos(Math.toRadians(state.getPredictedLatitude())));

        return state.toBuilder()
                .speedKmh(decayedSpeedKmh)
                .predictedLatitude(state.getPredictedLatitude() + dLat)
                .predictedLongitude(state.getPredictedLongitude() + dLon)
                .build();
    }

    private Mono<Void> broadcastPrediction(VehiclePredictionState state) {
        Double fractionValue = (state.getFractionOnRoute() >= 0) ? state.getFractionOnRoute() : null;

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
                state.getDirection() == 0,  // line=true for forward direction
                null,                       // nextStops — not available for predictions
                Boolean.TRUE,               // predicted = true
                fractionValue
        );

        return webSocketPublisher.broadcastVehiclePosition(msg)
                .doOnSuccess(v -> log.trace("Prediction broadcasted: vehicleId={}", state.getVehicleId()))
                .onErrorResume(e -> {
                    log.warn("Failed to broadcast prediction for vehicle {}: {}", state.getVehicleId(), e.getMessage());
                    return Mono.empty();
                });
    }

    public int getActiveStateCount() {
        return vehicleStates.size();
    }
}
