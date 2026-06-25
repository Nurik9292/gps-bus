package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Distance;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@Service
@Slf4j
@RequiredArgsConstructor
public class RouteGeometryTrimmingService {

    private final DistanceCalculationService distanceService;

    private static final Pattern LINESTRING_PATTERN = Pattern.compile(
            "LINESTRING\\s*\\(\\s*([^)]+)\\s*\\)", Pattern.CASE_INSENSITIVE
    );

    public String trimRouteGeometry(String fullGeometryWkt,
                                    BusStop fromStop,
                                    BusStop toStop) {
        try {
            if (fullGeometryWkt == null || fullGeometryWkt.trim().isEmpty()) {
                log.debug("No geometry to trim");
                return null;
            }

            List<double[]> coordinates = parseWktLineString(fullGeometryWkt);
            if (coordinates.size() < 2) {
                log.warn("Invalid geometry: less than 2 points");
                return buildMinimalGeometry(fromStop, toStop);
            }

            int fromSegment = findNearestSegmentIndex(coordinates, fromStop);
            int toSegment = findNearestSegmentIndex(coordinates, toStop);

            if (fromSegment < 0 || toSegment < 0) {
                log.warn("Could not find geometry segments near stops");
                return buildMinimalGeometry(fromStop, toStop);
            }

            List<double[]> trimmedCoordinates = new ArrayList<>();
            double[] fromPoint = {fromStop.getLongitude().doubleValue(), fromStop.getLatitude().doubleValue()};
            double[] toPoint = {toStop.getLongitude().doubleValue(), toStop.getLatitude().doubleValue()};

            if (fromSegment <= toSegment) {
                trimmedCoordinates.add(fromPoint);
                for (int i = fromSegment + 1; i <= toSegment; i++) {
                    trimmedCoordinates.add(coordinates.get(i));
                }
                trimmedCoordinates.add(toPoint);
            } else {
                trimmedCoordinates.add(fromPoint);
                for (int i = fromSegment; i > toSegment; i--) {
                    trimmedCoordinates.add(coordinates.get(i));
                }
                trimmedCoordinates.add(toPoint);
            }

            trimmedCoordinates = removeConsecutiveDuplicates(trimmedCoordinates);

            if (trimmedCoordinates.size() < 2) {
                return buildMinimalGeometry(fromStop, toStop);
            }

            String trimmedWkt = coordinatesToWkt(trimmedCoordinates);
            log.info("✅ GEOMETRY TRIMMED: {} → {} points (direction: {})",
                    coordinates.size(), trimmedCoordinates.size(),
                    fromSegment <= toSegment ? "forward" : "backward");
            return trimmedWkt;

        } catch (Exception e) {
            log.error("Failed to trim route geometry: {}", e.getMessage(), e);
            return buildMinimalGeometry(fromStop, toStop);
        }
    }

    private String buildMinimalGeometry(BusStop fromStop, BusStop toStop) {
        double fromLon = fromStop.getLongitude().doubleValue();
        double fromLat = fromStop.getLatitude().doubleValue();
        double toLon = toStop.getLongitude().doubleValue();
        double toLat = toStop.getLatitude().doubleValue();
        return String.format("LINESTRING(%.7f %.7f,%.7f %.7f)", fromLon, fromLat, toLon, toLat);
    }


    private List<double[]> parseWktLineString(String wkt) {
        List<double[]> coordinates = new ArrayList<>();

        Matcher matcher = LINESTRING_PATTERN.matcher(wkt.trim());
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid LINESTRING WKT: " + wkt);
        }

        String[] pairs = matcher.group(1).trim().split("\\s*,\\s*");
        for (String pair : pairs) {
            String[] lonLat = pair.trim().split("\\s+");
            if (lonLat.length >= 2) {
                try {
                    double lon = Double.parseDouble(lonLat[0]);
                    double lat = Double.parseDouble(lonLat[1]);
                    coordinates.add(new double[]{lon, lat});
                } catch (NumberFormatException e) {
                    log.warn("Invalid coordinate pair: {}", pair);
                }
            }
        }
        return coordinates;
    }


    private List<double[]> removeConsecutiveDuplicates(List<double[]> coordinates) {
        List<double[]> deduped = new ArrayList<>();
        for (double[] point : coordinates) {
            if (deduped.isEmpty() || !isSamePoint(deduped.get(deduped.size() - 1), point)) {
                deduped.add(point);
            }
        }
        return deduped;
    }

    private boolean isSamePoint(double[] a, double[] b) {
        return Math.abs(a[0] - b[0]) < 1e-7 && Math.abs(a[1] - b[1]) < 1e-7;
    }

    private String coordinatesToWkt(List<double[]> coordinates) {
        StringBuilder wkt = new StringBuilder("LINESTRING(");
        for (int i = 0; i < coordinates.size(); i++) {
            if (i > 0) wkt.append(",");
            double[] coord = coordinates.get(i);
            wkt.append(String.format("%.7f %.7f", coord[0], coord[1]));
        }
        wkt.append(")");
        return wkt.toString();
    }


    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        Distance distance = distanceService.calculateDistance(lat1, lon1, lat2, lon2);
        return distance.getMeters();
    }

    private int findNearestSegmentIndex(List<double[]> coordinates, BusStop stop) {
        double stopLat = stop.getLatitude().doubleValue();
        double stopLon = stop.getLongitude().doubleValue();

        double minDistance = Double.MAX_VALUE;
        int nearestIndex = -1;

        for (int i = 0; i < coordinates.size() - 1; i++) {
            double dist = distancePointToSegment(stopLat, stopLon,
                    coordinates.get(i)[1], coordinates.get(i)[0],
                    coordinates.get(i + 1)[1], coordinates.get(i + 1)[0]);
            if (dist < minDistance) {
                minDistance = dist;
                nearestIndex = i;
            }
        }

        return nearestIndex;
    }

    public String selectGeometryForDirection(String forwardGeom, String backwardGeom,
                                             BusStop fromStop, BusStop toStop) {
        if (forwardGeom != null && !forwardGeom.isBlank()) {
            try {
                List<double[]> coords = parseWktLineString(forwardGeom);
                int fromIdx = findNearestSegmentIndex(coords, fromStop);
                int toIdx = findNearestSegmentIndex(coords, toStop);
                if (fromIdx >= 0 && toIdx >= 0 && fromIdx <= toIdx) {
                    return forwardGeom;
                }
            } catch (Exception e) {
                log.warn("Failed to check forward geometry direction: {}", e.getMessage());
            }
        }

        if (backwardGeom != null && !backwardGeom.isBlank()) {
            try {
                List<double[]> coords = parseWktLineString(backwardGeom);
                int fromIdx = findNearestSegmentIndex(coords, fromStop);
                int toIdx = findNearestSegmentIndex(coords, toStop);
                if (fromIdx >= 0 && toIdx >= 0 && fromIdx <= toIdx) {
                    log.debug("Using BACKWARD geometry: fromIdx={}, toIdx={}", fromIdx, toIdx);
                    return backwardGeom;
                }
            } catch (Exception e) {
                log.warn("Failed to check backward geometry direction: {}", e.getMessage());
            }
        }

        return forwardGeom != null ? forwardGeom : backwardGeom;
    }

    public int calculateGeometryDistanceMeters(String wkt) {
        if (wkt == null || wkt.isBlank()) return 0;
        try {
            List<double[]> coords = parseWktLineString(wkt);
            double total = 0;
            for (int i = 0; i < coords.size() - 1; i++) {
                double lon1 = coords.get(i)[0], lat1 = coords.get(i)[1];
                double lon2 = coords.get(i + 1)[0], lat2 = coords.get(i + 1)[1];
                total += calculateHaversineDistance(lat1, lon1, lat2, lon2);
            }
            return (int) Math.round(total);
        } catch (Exception e) {
            log.warn("Failed to calculate geometry distance: {}", e.getMessage());
            return 0;
        }
    }

    private double distancePointToSegment(double px, double py,
                                          double x1, double y1,
                                          double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        if (dx == 0 && dy == 0) {
            return calculateHaversineDistance(px, py, x1, y1);
        }
        double t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy);
        t = Math.max(0, Math.min(1, t));
        double projX = x1 + t * dx;
        double projY = y1 + t * dy;
        return calculateHaversineDistance(px, py, projX, projY);
    }

    public boolean isValidGeometry(String wkt) {
        if (wkt == null || wkt.trim().isEmpty()) return false;
        try {
            List<double[]> coordinates = parseWktLineString(wkt);
            return coordinates.size() >= 2;
        } catch (Exception e) {
            return false;
        }
    }


}
