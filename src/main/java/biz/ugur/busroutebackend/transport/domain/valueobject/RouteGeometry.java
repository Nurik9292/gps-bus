package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@EqualsAndHashCode(callSuper = false)
public class RouteGeometry extends ValueObject {

    private final List<RoutePoint> points;

    public RouteGeometry(List<RoutePoint> points) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("Route geometry must contain at least one point");
        }

        if (points.size() < 2) {
            throw new IllegalArgumentException("Route geometry must contain at least 2 points for a valid LineString");
        }

        this.points = List.copyOf(points);
    }

    public static RouteGeometry fromCoordinates(List<List<Double>> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            throw new IllegalArgumentException("Coordinates cannot be empty");
        }

        List<RoutePoint> points = coordinates.stream()
                .map(coord -> {
                    if (coord.size() != 2) {
                        throw new IllegalArgumentException("Each coordinate must have exactly 2 values [longitude, latitude]");
                    }
                    return new RoutePoint(coord.get(0), coord.get(1));
                })
                .toList();

        return new RouteGeometry(points);
    }

    public static RouteGeometry fromCoordinateArrays(List<Double[]> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            throw new IllegalArgumentException("Coordinates cannot be empty");
        }

        List<RoutePoint> points = coordinates.stream()
                .map(coord -> {
                    if (coord.length != 2) {
                        throw new IllegalArgumentException("Each coordinate must have exactly 2 values [longitude, latitude]");
                    }
                    return new RoutePoint(coord[0], coord[1]);
                })
                .toList();

        return new RouteGeometry(points);
    }


    public String toWKT() {
        String pointsWKT = points.stream()
                .map(point -> String.format("%.6f %.6f", point.getLongitude(), point.getLatitude()))
                .collect(Collectors.joining(", "));

        return String.format("LINESTRING(%s)", pointsWKT);
    }

    public static RouteGeometry fromWKT(String wkt) {
        if (wkt == null || wkt.trim().isEmpty()) {
            throw new IllegalArgumentException("WKT string cannot be empty");
        }

        if (!wkt.startsWith("LINESTRING(") || !wkt.endsWith(")")) {
            throw new IllegalArgumentException("Invalid WKT format. Expected LINESTRING(...)");
        }


        String coordinatesStr = wkt.substring(11, wkt.length() - 1);

        if (coordinatesStr.trim().isEmpty()) {
            throw new IllegalArgumentException("WKT contains no coordinates");
        }

        String[] pointStrings = coordinatesStr.split(",");
        List<RoutePoint> points = Arrays.stream(pointStrings)
                .map(String::trim)
                .map(pointStr -> {
                    String[] coords = pointStr.split("\\s+");
                    if (coords.length != 2) {
                        throw new IllegalArgumentException("Invalid point format in WKT: " + pointStr);
                    }

                    try {
                        double longitude = Double.parseDouble(coords[0]);
                        double latitude = Double.parseDouble(coords[1]);
                        return new RoutePoint(longitude, latitude);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid coordinate values in WKT: " + pointStr, e);
                    }
                })
                .toList();

        return new RouteGeometry(points);
    }

    public List<List<Double>> toCoordinates() {
        return points.stream()
                .map(point -> List.of(point.getLongitude(), point.getLatitude()))
                .toList();
    }

    public List<Double[]> toCoordinateArrays() {
        return points.stream()
                .map(point -> new Double[]{point.getLongitude(), point.getLatitude()})
                .toList();
    }

    public double calculateDistanceMeters() {
        if (points.size() < 2) {
            return 0.0;
        }

        double totalDistance = 0.0;

        for (int i = 1; i < points.size(); i++) {
            RoutePoint prev = points.get(i - 1);
            RoutePoint curr = points.get(i);
            totalDistance += haversineDistance(prev, curr);
        }

        return totalDistance;
    }

    private double haversineDistance(RoutePoint point1, RoutePoint point2) {
        final double R = 6371000;

        double lat1Rad = Math.toRadians(point1.getLatitude());
        double lat2Rad = Math.toRadians(point2.getLatitude());
        double deltaLatRad = Math.toRadians(point2.getLatitude() - point1.getLatitude());
        double deltaLonRad = Math.toRadians(point2.getLongitude() - point1.getLongitude());

        double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    public int getPointCount() {
        return points.size();
    }

    public boolean isValid() {
        if (points.size() < 2) {
            return false;
        }


        return points.stream().allMatch(RoutePoint::isValid);
    }

    public RouteGeometry reverse() {
        List<RoutePoint> reversedPoints = points.reversed();
        return new RouteGeometry(reversedPoints);
    }

    public boolean containsPoint(RoutePoint point, double toleranceMeters) {
        return points.stream()
                .anyMatch(p -> haversineDistance(p, point) <= toleranceMeters);
    }

    @Override
    public String toString() {
        return String.format("RouteGeometry{points=%d, distance=%.1fm}",
                points.size(), calculateDistanceMeters());
    }
}