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

/**
 * Service for trimming route geometry between two stops.
 * Now uses centralized DistanceCalculationService for all distance calculations.
 */
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
                return fullGeometryWkt;
            }

            int startIndex = insertStopIntoGeometry(coordinates, fromStop);
            int endIndex = insertStopIntoGeometry(coordinates, toStop);

            if (startIndex < 0 || endIndex < 0) {
                log.warn("Could not find geometry points near stops");
                return fullGeometryWkt;
            }

            if (startIndex > endIndex) {
                Collections.reverse(coordinates);
                startIndex = coordinates.size() - 1 - startIndex;
                endIndex = coordinates.size() - 1 - endIndex;
            }

            List<double[]> trimmedCoordinates = new ArrayList<>(coordinates.subList(startIndex, endIndex + 1));

            if (trimmedCoordinates.size() < 2) {
                log.warn("Trimmed geometry too short: {} points", trimmedCoordinates.size());
                return fullGeometryWkt;
            }

            String trimmedWkt = coordinatesToWkt(trimmedCoordinates);

            log.info("✅ GEOMETRY TRIMMED EXACT: {} → {} points ({}% reduction)",
                    coordinates.size(),
                    trimmedCoordinates.size(),
                    Math.round((1.0 - (double) trimmedCoordinates.size() / coordinates.size()) * 100));

            return trimmedWkt;

        } catch (Exception e) {
            log.error("Failed to trim route geometry: {}", e.getMessage(), e);
            return fullGeometryWkt;
        }
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

    /**
     * Calculate Haversine distance using centralized service.
     *
     * @param lat1 Starting latitude
     * @param lon1 Starting longitude
     * @param lat2 Ending latitude
     * @param lon2 Ending longitude
     * @return Distance in meters
     */
    private double calculateHaversineDistance(double lat1, double lon1, double lat2, double lon2) {
        Distance distance = distanceService.calculateDistance(lat1, lon1, lat2, lon2);
        return distance.getMeters();
    }

    private int insertStopIntoGeometry(List<double[]> coordinates, BusStop stop) {
        double stopLat = stop.getLatitude().doubleValue();
        double stopLon = stop.getLongitude().doubleValue();

        double minDistance = Double.MAX_VALUE;
        int insertIndex = -1;

        for (int i = 0; i < coordinates.size() - 1; i++) {
            double dist = distancePointToSegment(stopLat, stopLon,
                    coordinates.get(i)[1], coordinates.get(i)[0],
                    coordinates.get(i + 1)[1], coordinates.get(i + 1)[0]);
            if (dist < minDistance) {
                minDistance = dist;
                insertIndex = i;
            }
        }

        if (insertIndex != -1) {
            coordinates.add(insertIndex + 1, new double[]{stopLon, stopLat});
            return insertIndex + 1;
        }

        return -1;
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
