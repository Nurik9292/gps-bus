package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@EqualsAndHashCode(callSuper = false)
public class RouteGeometry extends ValueObject {

    private final List<Coordinates> points;

    public RouteGeometry(List<Coordinates> points) {
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

        List<Coordinates> points = coordinates.stream()
                .map(coord -> {
                    if (coord.size() != 2) {
                        throw new IllegalArgumentException("Each coordinate must have exactly 2 values [latitude, longitude]");
                    }
                    return Coordinates.of(coord.get(0), coord.get(1));
                })
                .toList();

        return new RouteGeometry(points);
    }

    public static RouteGeometry fromCoordinateArrays(List<Double[]> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            throw new IllegalArgumentException("Coordinates cannot be empty");
        }

        List<Coordinates> points = coordinates.stream()
                .map(coord -> {
                    if (coord.length != 2) {
                        throw new IllegalArgumentException("Each coordinate must have exactly 2 values [latitude, longitude]");
                    }
                    return Coordinates.of(coord[0], coord[1]);
                })
                .toList();

        return new RouteGeometry(points);
    }


    public String toWKT() {
        String pointsWKT = points.stream()
                .map(point -> String.format("%.6f %.6f",
                    point.getLongitudeAsDouble(), point.getLatitudeAsDouble()))
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
        List<Coordinates> points = Arrays.stream(pointStrings)
                .map(String::trim)
                .map(pointStr -> {
                    String[] coords = pointStr.split("\\s+");
                    if (coords.length != 2) {
                        throw new IllegalArgumentException("Invalid point format in WKT: " + pointStr);
                    }

                    try {
                        double longitude = Double.parseDouble(coords[0]);
                        double latitude = Double.parseDouble(coords[1]);
                        return Coordinates.of(latitude, longitude);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid coordinate values in WKT: " + pointStr, e);
                    }
                })
                .toList();

        return new RouteGeometry(points);
    }

    public List<List<Double>> toCoordinates() {
        return points.stream()
                .map(point -> List.of(point.getLatitudeAsDouble(), point.getLongitudeAsDouble()))
                .toList();
    }

    public List<Double[]> toCoordinateArrays() {
        return points.stream()
                .map(point -> new Double[]{point.getLatitudeAsDouble(), point.getLongitudeAsDouble()})
                .toList();
    }



    public double calculateDistanceMeters(DistanceCalculationService distanceService) {
        if (points.size() < 2) {
            return 0.0;
        }

        if (distanceService == null) {
            throw new IllegalArgumentException("DistanceCalculationService cannot be null");
        }

        double totalDistance = 0.0;

        for (int i = 1; i < points.size(); i++) {
            Coordinates prev = points.get(i - 1);
            Coordinates curr = points.get(i);
            totalDistance += distanceService.calculateDistance(
                prev.getLatitudeAsDouble(), prev.getLongitudeAsDouble(),
                curr.getLatitudeAsDouble(), curr.getLongitudeAsDouble()
            ).getMeters();
        }

        return totalDistance;
    }

    public double calculateDistanceMetersSimple() {
        if (points.size() < 2) {
            return 0.0;
        }

        double totalDistance = 0.0;

        for (int i = 1; i < points.size(); i++) {
            Coordinates prev = points.get(i - 1);
            Coordinates curr = points.get(i);

            totalDistance += DistanceCalculationService.haversineDistanceMeters(
                    prev.getLatitudeAsDouble(), prev.getLongitudeAsDouble(),
                    curr.getLatitudeAsDouble(), curr.getLongitudeAsDouble()
            );
        }

        return totalDistance;
    }

    public int getPointCount() {
        return points.size();
    }

    public boolean isValid() {
        if (points.size() < 2) {
            return false;
        }

        return true;
    }

    public RouteGeometry reverse() {
        List<Coordinates> reversedPoints = points.reversed();
        return new RouteGeometry(reversedPoints);
    }



    public boolean containsPoint(Coordinates point, double toleranceMeters, DistanceCalculationService distanceService) {
        if (distanceService == null) {
            throw new IllegalArgumentException("DistanceCalculationService cannot be null");
        }

        return points.stream()
                .anyMatch(p -> distanceService.calculateDistance(
                    p.getLatitudeAsDouble(), p.getLongitudeAsDouble(),
                    point.getLatitudeAsDouble(), point.getLongitudeAsDouble()
                ).getMeters() <= toleranceMeters);
    }

    @Override
    public String toString() {
        return String.format("RouteGeometry{points=%d, distance=%.1fm}",
                points.size(), calculateDistanceMetersSimple());
    }
}