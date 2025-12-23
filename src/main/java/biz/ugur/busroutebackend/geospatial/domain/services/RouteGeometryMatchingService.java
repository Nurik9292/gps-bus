package biz.ugur.busroutebackend.geospatial.domain.services;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteGeometry;
import biz.ugur.busroutebackend.transport.infrastructure.redis.GpsPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for matching GPS tracks to route geometries.
 *
 * This service calculates how well a sequence of GPS points matches
 * a route's geometry, returning a confidence score.
 */
@Service
@Slf4j
public class RouteGeometryMatchingService {

    private final DistanceCalculationService distanceService;

    @Value("${business.gps-route-detection.max-distance-for-match-meters:100}")
    private double maxDistanceForMatch;

    @Value("${business.gps-route-detection.deviation-threshold-meters:500}")
    private double deviationThreshold;

    public RouteGeometryMatchingService(DistanceCalculationService distanceService) {
        this.distanceService = distanceService;
    }

    /**
     * Calculate match score between GPS track and route geometry.
     *
     * @param gpsPoints GPS points (in chronological order)
     * @param route Bus route with geometry
     * @return Match result with confidence score
     */
    public RouteMatchResult calculateMatchScore(List<GpsPoint> gpsPoints, BusRoute route) {
        if (gpsPoints == null || gpsPoints.isEmpty() || route == null) {
            return RouteMatchResult.noMatch("Insufficient data");
        }

        if (!route.hasGeometry()) {
            return RouteMatchResult.noMatch("Route has no geometry");
        }

        RouteGeometry forwardGeometry = route.getForwardGeometry();
        RouteGeometry backwardGeometry = route.getBackwardGeometry();

        double forwardScore = 0.0;
        double backwardScore = 0.0;

        if (forwardGeometry != null) {
            forwardScore = calculateGeometryMatchScore(gpsPoints, forwardGeometry);
        }

        if (backwardGeometry != null) {
            backwardScore = calculateGeometryMatchScore(gpsPoints, backwardGeometry);
        }

        double bestScore = Math.max(forwardScore, backwardScore);
        String direction = forwardScore >= backwardScore ? "forward" : "backward";
        int confidence = (int) Math.round(bestScore * 100);

        return new RouteMatchResult(
                route.getId().getValue(),
                route.getRouteNumber(),
                confidence,
                direction,
                bestScore >= 0.7,
                null
        );
    }

    /**
     * Calculate how well GPS points match a specific geometry.
     *
     * @param gpsPoints GPS points
     * @param geometry Route geometry
     * @return Score between 0.0 and 1.0
     */
    private double calculateGeometryMatchScore(List<GpsPoint> gpsPoints, RouteGeometry geometry) {
        List<Coordinates> routePoints = geometry.getPoints();
        if (routePoints.size() < 2) {
            return 0.0;
        }

        int matchingPoints = 0;
        double totalDistance = 0.0;

        for (GpsPoint gpsPoint : gpsPoints) {
            Coordinates gpsCoord = Coordinates.of(gpsPoint.getLat(), gpsPoint.getLon());
            double minDistance = calculateMinDistanceToRoute(gpsCoord, routePoints);

            totalDistance += minDistance;

            if (minDistance <= maxDistanceForMatch) {
                matchingPoints++;
            }
        }

        double matchingRatio = (double) matchingPoints / gpsPoints.size();
        double averageDistance = totalDistance / gpsPoints.size();

        double distanceScore = 1.0 - Math.min(averageDistance / deviationThreshold, 1.0);
        double finalScore = (matchingRatio * 0.6) + (distanceScore * 0.4);

        log.debug("Route match score: matching={}/{}, avgDist={:.1f}m, score={:.2f}",
                matchingPoints, gpsPoints.size(), averageDistance, finalScore);

        return Math.max(0.0, Math.min(1.0, finalScore));
    }

    /**
     * Calculate minimum distance from a point to any segment of the route.
     */
    private double calculateMinDistanceToRoute(Coordinates point, List<Coordinates> routePoints) {
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < routePoints.size() - 1; i++) {
            Coordinates segmentStart = routePoints.get(i);
            Coordinates segmentEnd = routePoints.get(i + 1);

            double distance = distanceService.calculatePointToLineDistance(
                    point, segmentStart, segmentEnd
            ).getMeters();

            minDistance = Math.min(minDistance, distance);
        }

        return minDistance;
    }

    /**
     * Check if vehicle is deviating from assigned route.
     *
     * @param gpsPoints Recent GPS points
     * @param route Assigned route
     * @return true if deviation detected
     */
    public boolean isDeviatingFromRoute(List<GpsPoint> gpsPoints, BusRoute route) {
        if (gpsPoints == null || gpsPoints.isEmpty() || route == null) {
            return false;
        }

        if (!route.hasGeometry()) {
            return false;
        }

        RouteMatchResult result = calculateMatchScore(gpsPoints, route);

        boolean deviating = result.confidence() < 50;

        if (deviating) {
            log.debug("Vehicle deviating from route {}: confidence={}%",
                    route.getRouteNumber(), result.confidence());
        }

        return deviating;
    }

    /**
     * Calculate average distance from GPS points to route.
     *
     * @param gpsPoints GPS points
     * @param route Bus route
     * @return Average distance in meters
     */
    public double calculateAverageDistanceFromRoute(List<GpsPoint> gpsPoints, BusRoute route) {
        if (gpsPoints == null || gpsPoints.isEmpty() || route == null || !route.hasGeometry()) {
            return Double.MAX_VALUE;
        }

        RouteGeometry geometry = route.getForwardGeometry();
        if (geometry == null) {
            geometry = route.getBackwardGeometry();
        }

        if (geometry == null) {
            return Double.MAX_VALUE;
        }

        List<Coordinates> routePoints = geometry.getPoints();
        double totalDistance = 0.0;

        for (GpsPoint gpsPoint : gpsPoints) {
            Coordinates gpsCoord = Coordinates.of(gpsPoint.getLat(), gpsPoint.getLon());
            totalDistance += calculateMinDistanceToRoute(gpsCoord, routePoints);
        }

        return totalDistance / gpsPoints.size();
    }

    /**
     * Result of route matching.
     */
    public record RouteMatchResult(
            String routeId,
            String routeNumber,
            int confidence,
            String direction,
            boolean isMatch,
            String reason
    ) {
        public static RouteMatchResult noMatch(String reason) {
            return new RouteMatchResult(null, null, 0, null, false, reason);
        }
    }
}
