package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.DirectVehiclePositionBroadcaster;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;


@Service
@Slf4j
public class VehiclePositionPredictionService {

    private static final double METRES_PER_DEGREE_LAT = 111_320.0;

    private static final double DT_SECONDS = 1.0;


    private static final double MAX_CORRECTION_DISTANCE_METERS = 50.0;

    private final ConcurrentHashMap<String, VehiclePredictionState> vehicleStates = new ConcurrentHashMap<>();

    private final PredictionProperties properties;
    private final DirectVehiclePositionBroadcaster directBroadcaster;
    private final RouteGeometryCache routeGeometryCache;
    private final MapMatchingService mapMatchingService;

    public VehiclePositionPredictionService(PredictionProperties properties,
                                             DirectVehiclePositionBroadcaster directBroadcaster,
                                             RouteGeometryCache routeGeometryCache,
                                             MapMatchingService mapMatchingService) {
        this.properties = properties;
        this.directBroadcaster = directBroadcaster;
        this.routeGeometryCache = routeGeometryCache;
        this.mapMatchingService = mapMatchingService;
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

        VehiclePredictionState existing = vehicleStates.get(vehicleId);

        if (existing != null && !timestamp.isAfter(existing.getLastGpsUpdate())) {
            log.trace("Ignoring duplicate GPS for vehicle {}: timestamp {} <= lastGpsUpdate {}",
                    vehicleId, timestamp, existing.getLastGpsUpdate());
            return;
        }

        double predictedLat;
        double predictedLon;
        double fraction;

        List<double[]> routeCoords = null;
        double totalDist = 0;

        if (routeNumber != null && properties.isSnapToRoute()) {
            routeCoords = routeGeometryCache.getPoints(routeNumber, direction);
            if (routeCoords == null) {
                int opposite = (direction == 0) ? 1 : 0;
                routeCoords = routeGeometryCache.getPoints(routeNumber, opposite);
                if (routeCoords != null) {
                    direction = opposite;
                }
            }
        }

        if (routeCoords != null) {
            // Route-aware mode
            totalDist = routeGeometryCache.getTotalDistance(routeNumber, direction);
            MapMatchingService.SnappedResult snap =
                    mapMatchingService.snapToNearestSegment(latitude, longitude, routeCoords);

            if (snap.snapped()) {
                double realFraction = snap.fraction();
                double predictedFraction = (existing != null) ? existing.getFractionOnRoute() : -1;

                boolean realIsAhead = (predictedFraction < 0)  // no prior state
                        || (direction == 0 && realFraction >= predictedFraction)
                        || (direction == 1 && realFraction <= predictedFraction);

                if (realIsAhead) {
                    // Bug 2 (route): real GPS is ahead — snap predicted forward
                    predictedLat = snap.latitude();
                    predictedLon = snap.longitude();
                    fraction = realFraction;
                    course = mapMatchingService.calculateCourseFromRoute(routeCoords, fraction, direction);
                } else {
                    predictedLat = existing.getPredictedLatitude();
                    predictedLon = existing.getPredictedLongitude();
                    fraction = existing.getFractionOnRoute();
                    course = mapMatchingService.calculateCourseFromRoute(routeCoords, fraction, direction);
                    log.trace("GPS behind predicted for vehicle {} (realFraction={}, predictedFraction={}); keeping predicted position",
                            vehicleId, realFraction, predictedFraction);
                }
            } else {
                predictedLat = applyBlendingIfClose(existing, latitude, longitude);
                predictedLon = applyBlendingIfClose_lon(existing, latitude, longitude);
                fraction = -1;
                routeCoords = null;
                totalDist = 0;
            }

        } else {
            if (existing == null) {
                predictedLat = latitude;
                predictedLon = longitude;
            } else {
                double dist = DistanceCalculationService.haversineDistanceMeters(
                        existing.getPredictedLatitude(), existing.getPredictedLongitude(),
                        latitude, longitude);

                if (dist <= MAX_CORRECTION_DISTANCE_METERS) {
                    double cf = properties.getCorrectionFactor();
                    predictedLat = existing.getPredictedLatitude() + cf * (latitude - existing.getPredictedLatitude());
                    predictedLon = existing.getPredictedLongitude() + cf * (longitude - existing.getPredictedLongitude());
                } else {
                    predictedLat = existing.getPredictedLatitude();
                    predictedLon = existing.getPredictedLongitude();
                    log.trace("GPS {}m from predicted for vehicle {}; keeping predicted position", (int) dist, vehicleId);
                }
            }
            fraction = -1;
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
        log.trace("GPS update stored: vehicleId={}, speed={}km/h, inMotion={}, fraction={}",
                vehicleId, speedKmh, inMotion, fraction);
    }

    public Mono<Void> predictNextPositions() {
        if (!properties.isEnabled()) {
            return Mono.empty();
        }

        cleanupStaleStates();

        Instant now = Instant.now();
        long maxAgeMs = properties.getMaxAgeMs();
        double minSpeed = properties.getMinSpeedKmh();

        List<VehiclePredictionState> activeStates = vehicleStates.values().stream()
                .filter(state -> state.isInMotion() && state.getSpeedKmh() >= minSpeed)
                .filter(state -> (now.toEpochMilli() - state.getLastGpsUpdate().toEpochMilli()) <= maxAgeMs)
                .filter(state -> !isAtRouteBoundary(state))
                .toList();

        if (activeStates.isEmpty()) {
            return Mono.empty();
        }

        log.trace("Prediction cycle: {} moving vehicles", activeStates.size());

        return Flux.fromIterable(activeStates)
                .flatMap(state -> {
                    VehiclePredictionState advanced = advanceState(state);
                    vehicleStates.put(advanced.getVehicleId(), advanced);
                    return broadcastPrediction(advanced);
                })
                .then();
    }

    // ---- private ----

    private void cleanupStaleStates() {
        Instant cutoff = Instant.now().minusSeconds(300);
        vehicleStates.entrySet().removeIf(e -> e.getValue().getLastGpsUpdate().isBefore(cutoff));
    }

    private boolean isAtRouteBoundary(VehiclePredictionState state) {
        if (state.getRouteCoordinates() == null || state.getFractionOnRoute() < 0) {
            return false;
        }
        double f = state.getFractionOnRoute();
        return (state.getDirection() == 0 && f >= 1.0)
                || (state.getDirection() == 1 && f <= 0.0);
    }


    private VehiclePredictionState advanceState(VehiclePredictionState state) {
        double decayedSpeedKmh = state.getSpeedKmh() * properties.getDecayFactor();

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
                state.getDirection() == 0,
                null,
                Boolean.TRUE,
                fractionValue
        );

        return Mono.fromRunnable(() -> {
            try {
                directBroadcaster.broadcastDirect(msg);
                log.trace("Prediction broadcasted direct: vehicleId={}", state.getVehicleId());
            } catch (Exception e) {
                log.warn("Failed to broadcast prediction for vehicle {}: {}", state.getVehicleId(), e.getMessage());
            }
        });
    }

    private double applyBlendingIfClose(VehiclePredictionState existing, double realLat, double realLon) {
        if (existing == null) return realLat;
        double dist = DistanceCalculationService.haversineDistanceMeters(
                existing.getPredictedLatitude(), existing.getPredictedLongitude(), realLat, realLon);
        if (dist <= MAX_CORRECTION_DISTANCE_METERS) {
            return existing.getPredictedLatitude()
                    + properties.getCorrectionFactor() * (realLat - existing.getPredictedLatitude());
        }
        return existing.getPredictedLatitude();
    }

    private double applyBlendingIfClose_lon(VehiclePredictionState existing, double realLat, double realLon) {
        if (existing == null) return realLon;
        double dist = DistanceCalculationService.haversineDistanceMeters(
                existing.getPredictedLatitude(), existing.getPredictedLongitude(), realLat, realLon);
        if (dist <= MAX_CORRECTION_DISTANCE_METERS) {
            return existing.getPredictedLongitude()
                    + properties.getCorrectionFactor() * (realLon - existing.getPredictedLongitude());
        }
        return existing.getPredictedLongitude();
    }

    public int getActiveStateCount() {
        return vehicleStates.size();
    }
}
