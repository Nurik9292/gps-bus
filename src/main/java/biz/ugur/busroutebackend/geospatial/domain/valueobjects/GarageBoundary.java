package biz.ugur.busroutebackend.geospatial.domain.valueobjects;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
@EqualsAndHashCode(callSuper = false)
public class GarageBoundary extends ValueObject {

    private static final int MINIMUM_POLYGON_POINTS = 4;
    private static final Pattern WKT_POLYGON_PATTERN = Pattern.compile(
            "POLYGON\\s*\\(\\s*\\((.+?)\\)\\s*\\)",
            Pattern.CASE_INSENSITIVE
    );

    private final List<Coordinates> points;

    private GarageBoundary(List<Coordinates> points) {
        validatePolygon(points);
        this.points = List.copyOf(points);
    }


    public static GarageBoundary of(List<Coordinates> points) {
        if (points == null || points.isEmpty()) {
            throw new IllegalArgumentException("Polygon points cannot be null or empty");
        }

        List<Coordinates> closedPoints = ensureClosed(points);
        return new GarageBoundary(closedPoints);
    }


    public static GarageBoundary fromWKT(String wkt) {
        if (wkt == null || wkt.trim().isEmpty()) {
            throw new IllegalArgumentException("WKT string cannot be null or empty");
        }

        Matcher matcher = WKT_POLYGON_PATTERN.matcher(wkt.trim());
        if (!matcher.find()) {
            throw new IllegalArgumentException("Invalid WKT POLYGON format: " + wkt);
        }

        String coordsString = matcher.group(1);
        List<Coordinates> points = parseCoordinatesString(coordsString);

        return new GarageBoundary(points);
    }

    public static GarageBoundary fromArray(double[][] coordPairs) {
        if (coordPairs == null || coordPairs.length < MINIMUM_POLYGON_POINTS - 1) {
            throw new IllegalArgumentException(
                    "Polygon must have at least " + (MINIMUM_POLYGON_POINTS - 1) + " points"
            );
        }

        List<Coordinates> points = new ArrayList<>();
        for (double[] pair : coordPairs) {
            if (pair.length != 2) {
                throw new IllegalArgumentException("Each coordinate pair must have exactly 2 values [lat, lon]");
            }
            points.add(Coordinates.of(pair[0], pair[1]));
        }

        return of(points);
    }


    public String toWKT() {
        StringBuilder sb = new StringBuilder("POLYGON((");

        for (int i = 0; i < points.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Coordinates point = points.get(i);
            sb.append(String.format("%.6f %.6f",
                    point.getLongitudeAsDouble(),
                    point.getLatitudeAsDouble()));
        }

        sb.append("))");
        return sb.toString();
    }


    public double[][][] toGeoJson() {
        double[][] ring = new double[points.size()][2];

        for (int i = 0; i < points.size(); i++) {
            Coordinates point = points.get(i);
            ring[i] = new double[]{
                    point.getLongitudeAsDouble(),
                    point.getLatitudeAsDouble()
            };
        }

        return new double[][][]{ring};
    }


    public int getPointCount() {
        return points.size();
    }

    public boolean isValid() {
        return points != null && points.size() >= MINIMUM_POLYGON_POINTS;
    }

    public Coordinates getCentroid() {
        double sumLat = 0;
        double sumLon = 0;
        int count = points.size() - 1;

        for (int i = 0; i < count; i++) {
            sumLat += points.get(i).getLatitudeAsDouble();
            sumLon += points.get(i).getLongitudeAsDouble();
        }

        return Coordinates.of(sumLat / count, sumLon / count);
    }

    private static List<Coordinates> parseCoordinatesString(String coordsString) {
        List<Coordinates> points = new ArrayList<>();
        String[] pairs = coordsString.split(",");

        for (String pair : pairs) {
            String[] values = pair.trim().split("\\s+");
            if (values.length != 2) {
                throw new IllegalArgumentException("Invalid coordinate pair: " + pair);
            }

            try {
                double lon = Double.parseDouble(values[0]);
                double lat = Double.parseDouble(values[1]);
                points.add(Coordinates.of(lat, lon));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid coordinate values: " + pair, e);
            }
        }

        return points;
    }

    private static List<Coordinates> ensureClosed(List<Coordinates> points) {
        if (points.isEmpty()) {
            return points;
        }

        Coordinates first = points.getFirst();
        Coordinates last = points.getLast();

        if (!first.equals(last)) {
            List<Coordinates> closed = new ArrayList<>(points);
            closed.add(first);
            return closed;
        }

        return points;
    }

    private void validatePolygon(List<Coordinates> points) {
        if (points == null) {
            throw new IllegalArgumentException("Polygon points cannot be null");
        }

        if (points.size() < MINIMUM_POLYGON_POINTS) {
            throw new IllegalArgumentException(
                    String.format("Polygon must have at least %d points (including closing point), got %d",
                            MINIMUM_POLYGON_POINTS, points.size())
            );
        }

        Coordinates first = points.getFirst();
        Coordinates last = points.getLast();
        if (!first.equals(last)) {
            throw new IllegalArgumentException("Polygon must be closed (first and last points must be equal)");
        }
    }

    @Override
    public String toString() {
        return String.format("GarageBoundary(points=%d, centroid=%s)",
                points.size(), getCentroid());
    }
}
