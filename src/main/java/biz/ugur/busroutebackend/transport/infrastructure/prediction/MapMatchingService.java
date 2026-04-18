package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class MapMatchingService {

  
    static final double MAX_SNAP_DISTANCE_METERS = 150.0;

    public SnappedResult snapToNearestSegment(double gpsLat, double gpsLon,
                                               List<double[]> routePoints,
                                               double totalRouteDistanceMeters) {
        if (routePoints == null || routePoints.size() < 2 || totalRouteDistanceMeters == 0) {
            return new SnappedResult(gpsLat, gpsLon, -1, Double.MAX_VALUE, false);
        }

        double minDist = Double.MAX_VALUE;
        double bestLat  = gpsLat, bestLon = gpsLon, bestFraction = 0;
        double accumLen = 0;

        for (int i = 0; i < routePoints.size() - 1; i++) {
            double[] a = routePoints.get(i);
            double[] b = routePoints.get(i + 1);
            double segLen = haversine(a, b);

            ProjectionResult proj = projectPointOnSegment(gpsLat, gpsLon, a, b);

            if (proj.distance < minDist) {
                minDist       = proj.distance;
                bestLat       = proj.lat;
                bestLon       = proj.lon;
                bestFraction  = (accumLen + segLen * proj.t) / totalRouteDistanceMeters;
            }

            accumLen += segLen;
        }

        if (minDist > MAX_SNAP_DISTANCE_METERS) {
            log.trace("GPS ({}, {}) is {}m from route — not snapping", gpsLat, gpsLon, (int) minDist);
            return new SnappedResult(gpsLat, gpsLon, -1, minDist, false);
        }

        return new SnappedResult(bestLat, bestLon, bestFraction, minDist, true);
    }


    public SnappedResult snapToNearestSegment(double gpsLat, double gpsLon,
                                               List<double[]> routePoints,
                                               double totalRouteDistanceMeters,
                                               double lastKnownFraction,
                                               double windowFraction) {
        if (lastKnownFraction < 0 || windowFraction <= 0) {
            return snapToNearestSegment(gpsLat, gpsLon, routePoints, totalRouteDistanceMeters);
        }

        if (routePoints == null || routePoints.size() < 2 || totalRouteDistanceMeters == 0) {
            return snapToNearestSegment(gpsLat, gpsLon, routePoints, totalRouteDistanceMeters);
        }

        double fracMin = Math.max(0, lastKnownFraction - windowFraction);
        double fracMax = Math.min(1, lastKnownFraction + windowFraction);

        double minDist = Double.MAX_VALUE;
        double bestLat = gpsLat, bestLon = gpsLon, bestFraction = 0;
        double accumLen = 0;

        for (int i = 0; i < routePoints.size() - 1; i++) {
            double[] a = routePoints.get(i);
            double[] b = routePoints.get(i + 1);
            double segLen = haversine(a, b);

            double segFracStart = accumLen / totalRouteDistanceMeters;
            double segFracEnd = (accumLen + segLen) / totalRouteDistanceMeters;

            boolean overlaps = segFracStart <= fracMax && segFracEnd >= fracMin;
            if (overlaps) {
                ProjectionResult proj = projectPointOnSegment(gpsLat, gpsLon, a, b);
                if (proj.distance < minDist) {
                    minDist = proj.distance;
                    bestLat = proj.lat;
                    bestLon = proj.lon;
                    bestFraction = (accumLen + segLen * proj.t) / totalRouteDistanceMeters;
                }
            }

            accumLen += segLen;
        }

        if (minDist <= MAX_SNAP_DISTANCE_METERS) {
            log.debug(String.format("[GPS_PIPELINE] SNAP_CONTINUITY window=[%.4f..%.4f] dist=%.1fm frac=%.4f",
                    fracMin, fracMax, minDist, bestFraction));
            return new SnappedResult(bestLat, bestLon, bestFraction, minDist, true);
        }

        log.debug(String.format("[GPS_PIPELINE] SNAP_CONTINUITY_MISS window=[%.4f..%.4f] → global search",
                fracMin, fracMax));
        return snapToNearestSegment(gpsLat, gpsLon, routePoints, totalRouteDistanceMeters);
    }

    public double[] interpolateRoutePoint(List<double[]> routePoints, double fraction,
                                          double totalRouteDistanceMeters) {
        if (routePoints == null || routePoints.isEmpty()) return null;
        if (fraction <= 0) return routePoints.get(0).clone();
        if (fraction >= 1) return routePoints.get(routePoints.size() - 1).clone();

        double target = fraction * totalRouteDistanceMeters;
        double accum  = 0;

        for (int i = 0; i < routePoints.size() - 1; i++) {
            double[] a = routePoints.get(i);
            double[] b = routePoints.get(i + 1);
            double segLen = haversine(a, b);

            if (accum + segLen >= target) {
                double t = (segLen == 0) ? 0 : (target - accum) / segLen;
                return new double[]{
                        a[0] + t * (b[0] - a[0]),
                        a[1] + t * (b[1] - a[1])
                };
            }
            accum += segLen;
        }

        return routePoints.get(routePoints.size() - 1).clone();
    }

  
    public double calculateCourseFromRoute(List<double[]> routePoints, double fraction,
                                           int direction, double totalRouteDistanceMeters) {
        if (routePoints == null || routePoints.size() < 2) return 0;

        double target  = fraction * totalRouteDistanceMeters;
        double accum   = 0;

        int segIndex = routePoints.size() - 2;
        for (int i = 0; i < routePoints.size() - 1; i++) {
            double segLen = haversine(routePoints.get(i), routePoints.get(i + 1));
            accum += segLen;
            if (accum >= target) {
                segIndex = i;
                break;
            }
        }

        double[] a = routePoints.get(segIndex);
        double[] b = routePoints.get(segIndex + 1);
        double bearing = bearingDegrees(a[0], a[1], b[0], b[1]);

        return bearing;
    }

    private ProjectionResult projectPointOnSegment(double lat, double lon,
                                                    double[] a, double[] b) {
        double midLat = (a[0] + b[0]) / 2.0;
        double metersPerDegreeLon = METRES_PER_DEGREE_LAT * Math.cos(Math.toRadians(midLat));

        double dyM = (b[0] - a[0]) * METRES_PER_DEGREE_LAT;
        double dxM = (b[1] - a[1]) * metersPerDegreeLon;     

        if (dyM == 0 && dxM == 0) {
            return new ProjectionResult(a[0], a[1], haversine(new double[]{lat, lon}, a), 0);
        }

        double pyM = (lat - a[0]) * METRES_PER_DEGREE_LAT;
        double pxM = (lon - a[1]) * metersPerDegreeLon;

        double t = (pyM * dyM + pxM * dxM) / (dyM * dyM + dxM * dxM);
        t = Math.max(0, Math.min(1, t));

        double projLat = a[0] + t * (b[0] - a[0]);
        double projLon = a[1] + t * (b[1] - a[1]);
        double dist = DistanceCalculationService.haversineDistanceMeters(lat, lon, projLat, projLon);

        return new ProjectionResult(projLat, projLon, dist, t);
    }

    private static final double METRES_PER_DEGREE_LAT = 111_320.0;

    private static double haversine(double[] a, double[] b) {
        return DistanceCalculationService.haversineDistanceMeters(a[0], a[1], b[0], b[1]);
    }

   
    private static double bearingDegrees(double lat1, double lon1, double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        double rlat1 = Math.toRadians(lat1);
        double rlat2 = Math.toRadians(lat2);

        double x = Math.sin(dLon) * Math.cos(rlat2);
        double y = Math.cos(rlat1) * Math.sin(rlat2) - Math.sin(rlat1) * Math.cos(rlat2) * Math.cos(dLon);

        double bearing = Math.toDegrees(Math.atan2(x, y));
        return (bearing + 360) % 360;
    }


    public record SnappedResult(double latitude, double longitude,
                                 double fraction, double distanceMeters,
                                 boolean snapped) {}

    private record ProjectionResult(double lat, double lon, double distance, double t) {}
}
