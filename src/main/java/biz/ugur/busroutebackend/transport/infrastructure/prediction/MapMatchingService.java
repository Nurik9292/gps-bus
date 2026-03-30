package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Pure-Java geometry utilities for map matching and route-aware interpolation.
 * All calculations run in-memory — no database calls.
 */
@Service
@Slf4j
public class MapMatchingService {

    /** If the GPS point is further than this from the route, do not snap */
    static final double MAX_SNAP_DISTANCE_METERS = 100.0;

    // ---- Public API ----

    /**
     * Projects {@code (gpsLat, gpsLon)} onto the nearest segment of {@code routePoints}.
     * Returns the snapped position with its fraction along the route (0.0–1.0).
     * If the nearest segment is more than {@link #MAX_SNAP_DISTANCE_METERS} away,
     * {@link SnappedResult#snapped} is {@code false} and the original GPS is returned unchanged.
     */
    public SnappedResult snapToNearestSegment(double gpsLat, double gpsLon,
                                               List<double[]> routePoints) {
        if (routePoints == null || routePoints.size() < 2) {
            return new SnappedResult(gpsLat, gpsLon, -1, Double.MAX_VALUE, false);
        }

        double totalLength = totalDistance(routePoints);
        if (totalLength == 0) {
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
                bestFraction  = (accumLen + segLen * proj.t) / totalLength;
            }

            accumLen += segLen;
        }

        if (minDist > MAX_SNAP_DISTANCE_METERS) {
            log.trace("GPS ({}, {}) is {}m from route — not snapping", gpsLat, gpsLon, (int) minDist);
            return new SnappedResult(gpsLat, gpsLon, -1, minDist, false);
        }

        return new SnappedResult(bestLat, bestLon, bestFraction, minDist, true);
    }

    /**
     * Interpolates the geographical position at {@code fraction} (0.0–1.0) along {@code routePoints}.
     * Returns the last point if fraction ≥ 1.0, first point if ≤ 0.0.
     *
     * @return {@code [lat, lon]} pair
     */
    public double[] interpolateRoutePoint(List<double[]> routePoints, double fraction) {
        if (routePoints == null || routePoints.isEmpty()) return null;
        if (fraction <= 0) return routePoints.get(0).clone();
        if (fraction >= 1) return routePoints.get(routePoints.size() - 1).clone();

        double totalLen = totalDistance(routePoints);
        double target   = fraction * totalLen;
        double accum    = 0;

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

    /**
     * Derives the vehicle heading (degrees, 0=North, 90=East) from the direction of
     * the route segment at the given {@code fraction}.
     * For backward direction the bearing is reversed by 180°.
     */
    public double calculateCourseFromRoute(List<double[]> routePoints, double fraction, int direction) {
        if (routePoints == null || routePoints.size() < 2) return 0;

        // Find the segment index at this fraction
        double totalLen = totalDistance(routePoints);
        double target   = fraction * totalLen;
        double accum    = 0;

        int segIndex = routePoints.size() - 2; // default: last segment
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

        return (direction == 1) ? (bearing + 180) % 360 : bearing;
    }

    // ---- Private geometry helpers ----

    /**
     * Projects point {@code (lat, lon)} onto segment {@code a→b}.
     * All coordinates are in degrees; result coordinates and distance in metres.
     */
    private ProjectionResult projectPointOnSegment(double lat, double lon,
                                                    double[] a, double[] b) {
        double dy = b[0] - a[0]; // lat delta
        double dx = b[1] - a[1]; // lon delta

        if (dy == 0 && dx == 0) {
            // Degenerate segment (zero-length)
            return new ProjectionResult(a[0], a[1], haversine(new double[]{lat, lon}, a), 0);
        }

        double t = ((lat - a[0]) * dy + (lon - a[1]) * dx) / (dy * dy + dx * dx);
        t = Math.max(0, Math.min(1, t));

        double projLat = a[0] + t * dy;
        double projLon = a[1] + t * dx;
        double dist = DistanceCalculationService.haversineDistanceMeters(lat, lon, projLat, projLon);

        return new ProjectionResult(projLat, projLon, dist, t);
    }

    private static double haversine(double[] a, double[] b) {
        return DistanceCalculationService.haversineDistanceMeters(a[0], a[1], b[0], b[1]);
    }

    private static double totalDistance(List<double[]> points) {
        double d = 0;
        for (int i = 0; i < points.size() - 1; i++) {
            d += haversine(points.get(i), points.get(i + 1));
        }
        return d;
    }

    /**
     * Forward azimuth (bearing) from point A to point B, in degrees [0, 360).
     */
    private static double bearingDegrees(double lat1, double lon1, double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        double rlat1 = Math.toRadians(lat1);
        double rlat2 = Math.toRadians(lat2);

        double x = Math.sin(dLon) * Math.cos(rlat2);
        double y = Math.cos(rlat1) * Math.sin(rlat2) - Math.sin(rlat1) * Math.cos(rlat2) * Math.cos(dLon);

        double bearing = Math.toDegrees(Math.atan2(x, y));
        return (bearing + 360) % 360;
    }

    // ---- Result types ----

    public record SnappedResult(double latitude, double longitude,
                                 double fraction, double distanceMeters,
                                 boolean snapped) {}

    private record ProjectionResult(double lat, double lon, double distance, double t) {}
}
