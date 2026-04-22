package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;


@Component
@Slf4j
public class RouteGeometryCache {

  
    public static final String FORWARD  = "_0";

    public static final String BACKWARD = "_1";

    private final ConcurrentHashMap<String, List<double[]>>    pointsCache        = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double>            distanceCache      = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, double[]>          stopFractionsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<RouteStopInfo>> routeStopsCache  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String>            routeNameCache     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String>            routeColorCache    = new ConcurrentHashMap<>();

    private final BusRouteRepository busRouteRepository;

    public RouteGeometryCache(BusRouteRepository busRouteRepository) {
        this.busRouteRepository = busRouteRepository;
    }

    private volatile boolean loaded = false;

    @PostConstruct
    public void init() {
        try {
            loadWithRetry().block(Duration.ofSeconds(60));
            loaded = true;
            log.info("Route geometry cache loaded synchronously: {} geometry entries, {} stop-fraction entries",
                    pointsCache.size(), stopFractionsCache.size());
        } catch (RuntimeException e) {
            log.error("Route geometry cache init timed out — prediction will use dead-reckoning until cache warms up: {}",
                    e.getMessage());
        }
    }

    public boolean isLoaded() {
        return loaded;
    }

    private reactor.core.publisher.Mono<Void> loadWithRetry() {
        return busRouteRepository.findActiveRoutes()
                .doOnNext(this::cacheRoute)
                .flatMap(route -> loadStopFractions(route.getRouteNumber()))
                .retryWhen(reactor.util.retry.Retry.backoff(5, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(32))
                        .doBeforeRetry(signal -> log.warn(
                                "Route geometry cache load failed (attempt {}), retrying in {}s: {}",
                                signal.totalRetries() + 1,
                                Math.min(2 << (int) signal.totalRetries(), 32),
                                signal.failure().getMessage())))
                .doOnError(e -> log.error(
                        "Route geometry cache failed after all retries — prediction will use dead-reckoning: {}",
                        e.getMessage()))
                .then();
    }

    private reactor.core.publisher.Mono<Void> loadStopFractions(String routeNumber) {
        return reactor.core.publisher.Flux.fromIterable(List.of(0, 1))
                .flatMap(dir -> busRouteRepository.getRouteStopsInfoByNumber(routeNumber, dir)
                        .collectList()
                        .doOnSuccess(stops -> cacheStopFractions(routeNumber, dir, stops)))
                .then()
                .onErrorResume(e -> {
                    log.debug("Stop fractions load skipped for route {}: {}", routeNumber, e.getMessage());
                    return reactor.core.publisher.Mono.empty();
                });
    }

    private void cacheStopFractions(String routeNumber, int direction, List<RouteStopInfo> stops) {
        String key = routeNumber + (direction == 0 ? FORWARD : BACKWARD);
        double totalDistance = distanceCache.getOrDefault(key, 0.0);
        if (totalDistance <= 0 || stops == null || stops.isEmpty()) return;

        List<RouteStopInfo> sorted = stops.stream()
                .filter(s -> s.getDistanceFromStartMeters() != null && s.getDistanceFromStartMeters() >= 0)
                .sorted(Comparator.comparingInt(RouteStopInfo::getDistanceFromStartMeters))
                .toList();

        double[] fractions = sorted.stream()
                .mapToDouble(s -> s.getDistanceFromStartMeters() / totalDistance)
                .toArray();

        if (fractions.length > 0) {
            stopFractionsCache.put(key, fractions);
            routeStopsCache.put(key, sorted);
            log.debug("Cached {} stop fractions for route {} dir={}", fractions.length, routeNumber, direction);
        }
    }


    public List<double[]> getPoints(String routeNumber, int direction) {
        String key = routeNumber + (direction == 0 ? FORWARD : BACKWARD);
        return pointsCache.get(key);
    }

    
    public double getTotalDistance(String routeNumber, int direction) {
        String key = routeNumber + (direction == 0 ? FORWARD : BACKWARD);
        return distanceCache.getOrDefault(key, 0.0);
    }

 
    public double[] getStopFractions(String routeNumber, int direction) {
        String key = routeNumber + (direction == 0 ? FORWARD : BACKWARD);
        return stopFractionsCache.get(key);
    }

   
    public List<RouteStopInfo> getRouteStops(String routeNumber, int direction) {
        String key = routeNumber + (direction == 0 ? FORWARD : BACKWARD);
        List<RouteStopInfo> stops = routeStopsCache.get(key);
        return stops != null ? stops : Collections.emptyList();
    }


    public List<RouteStopInfo> getStopsAhead(String routeNumber, int direction, double currentFraction) {
        double totalDistance = getTotalDistance(routeNumber, direction);
        if (totalDistance <= 0) return Collections.emptyList();
        return getRouteStops(routeNumber, direction).stream()
                .filter(s -> s.getDistanceFromStartMeters() / totalDistance > currentFraction + 0.0001)
                .toList();
    }

    public Optional<RouteStopInfo> getNextStop(String routeNumber, int direction, double currentFraction) {
        double totalDistance = getTotalDistance(routeNumber, direction);
        if (totalDistance <= 0) return Optional.empty();
        return getRouteStops(routeNumber, direction).stream()
                .filter(s -> s.getDistanceFromStartMeters() != null
                        && s.getDistanceFromStartMeters() / totalDistance > currentFraction + 0.0001)
                .findFirst();
    }

 
    public OptionalDouble getStopFraction(String routeNumber, int direction, String stopId) {
        double totalDistance = getTotalDistance(routeNumber, direction);
        if (totalDistance <= 0) return OptionalDouble.empty();
        return getRouteStops(routeNumber, direction).stream()
                .filter(s -> stopId.equals(s.getStopId()) && s.getDistanceFromStartMeters() != null)
                .mapToDouble(s -> s.getDistanceFromStartMeters() / totalDistance)
                .findFirst();
    }

    public OptionalDouble getStopFractionByName(String routeNumber, int direction, String stopName) {
        if (stopName == null || stopName.isBlank()) return OptionalDouble.empty();
        double totalDistance = getTotalDistance(routeNumber, direction);
        if (totalDistance <= 0) return OptionalDouble.empty();
        return getRouteStops(routeNumber, direction).stream()
                .filter(s -> stopName.equalsIgnoreCase(s.getStopName()) && s.getDistanceFromStartMeters() != null)
                .mapToDouble(s -> s.getDistanceFromStartMeters() / totalDistance)
                .findFirst();
    }

    public OptionalDouble getStopFractionByCoordinates(String routeNumber, int direction,
                                                        double lat, double lon, double maxDistanceMeters) {
        double totalDistance = getTotalDistance(routeNumber, direction);
        if (totalDistance <= 0) return OptionalDouble.empty();
        RouteStopInfo nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (RouteStopInfo s : getRouteStops(routeNumber, direction)) {
            if (s.getDistanceFromStartMeters() == null) continue;
            double dist = haversineMeters(lat, lon,
                    s.getLatitude().doubleValue(), s.getLongitude().doubleValue());
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = s;
            }
        }
        if (nearest == null || nearestDist > maxDistanceMeters) return OptionalDouble.empty();
        return OptionalDouble.of(nearest.getDistanceFromStartMeters() / totalDistance);
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    public void refreshRoute(String routeNumber) {
        busRouteRepository.findByRouteNumber(routeNumber)
                .doOnNext(this::cacheRoute)
                .flatMap(route -> loadStopFractions(routeNumber))
                .subscribe(
                        ignored -> {},
                        error -> log.error("Failed to refresh route {}: {}", routeNumber, error.getMessage()),
                        () -> log.info("Refreshed route geometry cache for route {}", routeNumber)
                );
    }


    private void cacheRoute(BusRoute route) {
        String routeNumber = route.getRouteNumber();
        if (route.getRouteName() != null) {
            routeNameCache.put(routeNumber, route.getRouteName());
        }
        if (route.getRouteColor() != null) {
            routeColorCache.put(routeNumber, route.getRouteColor());
        }
        cacheGeometry(routeNumber, FORWARD,  route.getRouteGeometryForward());
        cacheGeometry(routeNumber, BACKWARD, route.getRouteGeometryBackward());
    }

    public String getRouteName(String routeNumber) {
        return routeNameCache.get(routeNumber);
    }

    public String getRouteColor(String routeNumber) {
        return routeColorCache.get(routeNumber);
    }

    private void cacheGeometry(String routeNumber, String suffix, String wkt) {
        if (wkt == null || wkt.isBlank()) return;

        try {
            List<double[]> points = parseWkt(wkt);
            if (points.size() < 2) return;

            String key = routeNumber + suffix;
            pointsCache.put(key, points);
            distanceCache.put(key, computeTotalDistance(points));
        } catch (Exception e) {
            log.warn("Cannot parse WKT for route {} {}: {}", routeNumber, suffix, e.getMessage());
        }
    }

   
    static List<double[]> parseWkt(String wkt) {
        String trimmed = wkt.trim();
        if (!trimmed.startsWith("LINESTRING(") || !trimmed.endsWith(")")) {
            throw new IllegalArgumentException("Expected LINESTRING(...): " + trimmed);
        }
        String inner = trimmed.substring(11, trimmed.length() - 1).trim();
        String[] tokens = inner.split(",");
        double[][] result = new double[tokens.length][2];
        for (int i = 0; i < tokens.length; i++) {
            String[] parts = tokens[i].trim().split("\\s+");
            if (parts.length < 2) throw new IllegalArgumentException("Bad point: " + tokens[i]);
            double lon = Double.parseDouble(parts[0]);
            double lat = Double.parseDouble(parts[1]);
            result[i] = new double[]{lat, lon}; 
        }
        return List.of(result);
    }

    private static double computeTotalDistance(List<double[]> points) {
        double total = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            total += DistanceCalculationService.haversineDistanceMeters(
                    points.get(i)[0], points.get(i)[1],
                    points.get(i + 1)[0], points.get(i + 1)[1]);
        }
        return total;
    }
}
