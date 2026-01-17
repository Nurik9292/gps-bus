package biz.ugur.busroutebackend.geospatial.domain.services;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteGeometry;
import lombok.extern.slf4j.Slf4j;

import java.util.List;


@Slf4j
public class RouteGeometryMatchingService {

    private final DistanceCalculationService distanceService;
    private final double maxDistanceForMatch;
    private final double deviationThreshold;

    public RouteGeometryMatchingService(DistanceCalculationService distanceService,
                                         double maxDistanceForMatch,
                                         double deviationThreshold) {
        this.distanceService = distanceService;
        this.maxDistanceForMatch = maxDistanceForMatch;
        this.deviationThreshold = deviationThreshold;
    }


    public record GpsPoint(double lat, double lon, Double speed, Long timestamp) {

    }


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


    private double calculateGeometryMatchScore(List<GpsPoint> gpsPoints, RouteGeometry geometry) {
        List<Coordinates> routePoints = geometry.getPoints();
        if (routePoints.size() < 2) {
            return 0.0;
        }

        int matchingPoints = 0;
        double totalDistance = 0.0;

        for (GpsPoint gpsPoint : gpsPoints) {
            Coordinates gpsCoord = Coordinates.of(gpsPoint.lat, gpsPoint.lat);
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
            Coordinates gpsCoord = Coordinates.of(gpsPoint.lat(), gpsPoint.lon);
            totalDistance += calculateMinDistanceToRoute(gpsCoord, routePoints);
        }

        return totalDistance / gpsPoints.size();
    }


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
