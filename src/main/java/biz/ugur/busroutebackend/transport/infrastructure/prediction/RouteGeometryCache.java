package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of route geometry points.
 * Key format: {@code routeNumber + "_0"} (forward) or {@code routeNumber + "_1"} (backward).
 * Each value is a list of {@code [lat, lon]} pairs derived from the route's WKT LINESTRING.
 *
 * Loaded once at startup; a route change event should call {@link #refreshRoute(String)} as needed.
 */
@Component
@Slf4j
public class RouteGeometryCache {

    /** Key suffix for forward direction */
    public static final String FORWARD  = "_0";
    /** Key suffix for backward direction */
    public static final String BACKWARD = "_1";

    private final ConcurrentHashMap<String, List<double[]>> pointsCache    = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double>         distanceCache  = new ConcurrentHashMap<>();

    private final BusRouteRepository busRouteRepository;

    public RouteGeometryCache(BusRouteRepository busRouteRepository) {
        this.busRouteRepository = busRouteRepository;
    }

    @PostConstruct
    public void init() {
        loadWithRetry();
    }

    /**
     * Loads all active route geometries with exponential-backoff retry.
     * If the R2DBC connection is not yet ready at startup (common in Docker),
     * the previous implementation failed permanently — leaving the cache empty
     * and causing all buses to fall into dead-reckoning until app restart.
     * Now retries up to 5 times: delays 2s, 4s, 8s, 16s, 32s.
     */
    private void loadWithRetry() {
        busRouteRepository.findActiveRoutes()
                .doOnNext(this::cacheRoute)
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
                .subscribe(
                        route -> {},
                        error -> log.error("Route geometry cache init error (all retries exhausted)", error),
                        () -> log.info("Route geometry cache loaded: {} entries", pointsCache.size())
                );
    }

    /**
     * Returns cached point list for a route/direction key, or {@code null} if absent or empty.
     */
    public List<double[]> getPoints(String routeNumber, int direction) {
        String key = routeNumber + (direction == 0 ? FORWARD : BACKWARD);
        return pointsCache.get(key);
    }

    /**
     * Total distance of a cached route in metres, or {@code 0} if absent.
     */
    public double getTotalDistance(String routeNumber, int direction) {
        String key = routeNumber + (direction == 0 ? FORWARD : BACKWARD);
        return distanceCache.getOrDefault(key, 0.0);
    }

    /**
     * Re-fetch a single route from the DB and update its cache entries.
     * Useful when a route geometry changes.
     */
    public void refreshRoute(String routeNumber) {
        busRouteRepository.findByRouteNumber(routeNumber)
                .doOnNext(this::cacheRoute)
                .subscribe(
                        route -> log.info("Refreshed route geometry cache for route {}", routeNumber),
                        error -> log.error("Failed to refresh route {}: {}", routeNumber, error.getMessage())
                );
    }

    // ---- helpers ----

    private void cacheRoute(BusRoute route) {
        String routeNumber = route.getRouteNumber();
        cacheGeometry(routeNumber, FORWARD,  route.getRouteGeometryForward());
        cacheGeometry(routeNumber, BACKWARD, route.getRouteGeometryBackward());
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

    /**
     * Parses {@code LINESTRING(lon1 lat1, lon2 lat2, ...)} into {@code [lat, lon]} pairs.
     * WKT stores coordinates as lon lat (GIS convention); we flip to [lat, lon] internally.
     */
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
            result[i] = new double[]{lat, lon};  // store as [lat, lon]
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
