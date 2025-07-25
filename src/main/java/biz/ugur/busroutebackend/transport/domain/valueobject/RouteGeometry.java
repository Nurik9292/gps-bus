package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.ValueObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@EqualsAndHashCode(callSuper = false)
public class RouteGeometry extends ValueObject {

    private final List<RoutePoint> coordinates;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public RouteGeometry(List<RoutePoint> coordinates) {
        if (coordinates == null || coordinates.size() < 2) {
            throw new IllegalArgumentException("Route must have at least 2 coordinate points");
        }
        this.coordinates = new ArrayList<>(coordinates);
    }

    public static RouteGeometry fromGeoJson(String geoJsonString) {
        try {
            JsonNode root = objectMapper.readTree(geoJsonString);

            if (!"LineString".equals(root.get("type").asText())) {
                throw new IllegalArgumentException("GeoJSON must be of type LineString");
            }

            JsonNode coordinatesNode = root.get("coordinates");
            List<RoutePoint> points = new ArrayList<>();

            for (JsonNode coordArray : coordinatesNode) {
                double longitude = coordArray.get(0).asDouble();
                double latitude = coordArray.get(1).asDouble();
                points.add(new RoutePoint(latitude, longitude));
            }

            return new RouteGeometry(points);

        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid GeoJSON format", e);
        }
    }

    public static RouteGeometry fromCoordinates(List<Double[]> coordinates) {
        List<RoutePoint> points = coordinates.stream()
                .map(coord -> new RoutePoint(coord[0], coord[1]))
                .toList();
        return new RouteGeometry(points);
    }

    public String toGeoJson() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"type\":\"LineString\",\"coordinates\":[");

            for (int i = 0; i < coordinates.size(); i++) {
                RoutePoint point = coordinates.get(i);
                sb.append(String.format("[%.6f,%.6f]", point.getLongitude(), point.getLatitude()));
                if (i < coordinates.size() - 1) {
                    sb.append(",");
                }
            }

            sb.append("]}");
            return sb.toString();

        } catch (Exception e) {
            throw new RuntimeException("Failed to convert to GeoJSON", e);
        }
    }

    public void validate() {
        if (coordinates.isEmpty()) {
            throw new IllegalArgumentException("Route geometry cannot be empty");
        }

        if (coordinates.size() < 2) {
            throw new IllegalArgumentException("Route must have at least 2 points");
        }

        for (RoutePoint point : coordinates) {
            point.validate();
        }

        for (int i = 1; i < coordinates.size(); i++) {
            RoutePoint prev = coordinates.get(i - 1);
            RoutePoint curr = coordinates.get(i);

            if (prev.distanceTo(curr) < 5.0) { // Минимум 5 метров между точками
                throw new IllegalArgumentException("Route points are too close together at index " + i);
            }
        }
    }

    public Integer calculateTotalDistance() {
        double totalDistance = 0.0;

        for (int i = 1; i < coordinates.size(); i++) {
            RoutePoint prev = coordinates.get(i - 1);
            RoutePoint curr = coordinates.get(i);
            totalDistance += prev.distanceTo(curr);
        }

        return (int) Math.round(totalDistance);
    }

    public RoutePoint getPointAtDistance(double distanceMeters) {
        if (distanceMeters <= 0) {
            return coordinates.getFirst();
        }

        double accumulatedDistance = 0.0;

        for (int i = 1; i < coordinates.size(); i++) {
            RoutePoint prev = coordinates.get(i - 1);
            RoutePoint curr = coordinates.get(i);
            double segmentDistance = prev.distanceTo(curr);

            if (accumulatedDistance + segmentDistance >= distanceMeters) {
                double ratio = (distanceMeters - accumulatedDistance) / segmentDistance;
                return interpolatePoint(prev, curr, ratio);
            }

            accumulatedDistance += segmentDistance;
        }

        return coordinates.getLast();
    }

    public RoutePoint getNearestPointOnRoute(double latitude, double longitude) {
        RoutePoint targetPoint = new RoutePoint(latitude, longitude);
        RoutePoint nearestPoint = coordinates.getFirst();
        double minDistance = nearestPoint.distanceTo(targetPoint);

        for (int i = 1; i < coordinates.size(); i++) {
            RoutePoint prev = coordinates.get(i - 1);
            RoutePoint curr = coordinates.get(i);

            RoutePoint nearestOnSegment = getNearestPointOnSegment(prev, curr, targetPoint);
            double distance = nearestOnSegment.distanceTo(targetPoint);

            if (distance < minDistance) {
                minDistance = distance;
                nearestPoint = nearestOnSegment;
            }
        }

        return nearestPoint;
    }

    public int getCoordinatesCount() {
        return coordinates.size();
    }


    private RoutePoint interpolatePoint(RoutePoint p1, RoutePoint p2, double ratio) {
        double lat = p1.getLatitude() + (p2.getLatitude() - p1.getLatitude()) * ratio;
        double lon = p1.getLongitude() + (p2.getLongitude() - p1.getLongitude()) * ratio;
        return new RoutePoint(lat, lon);
    }

    private RoutePoint getNearestPointOnSegment(RoutePoint p1, RoutePoint p2, RoutePoint target) {
        double dx = p2.getLongitude() - p1.getLongitude();
        double dy = p2.getLatitude() - p1.getLatitude();

        if (dx == 0 && dy == 0) {
            return p1;
        }

        double t = ((target.getLongitude() - p1.getLongitude()) * dx +
                (target.getLatitude() - p1.getLatitude()) * dy) / (dx * dx + dy * dy);

        t = Math.max(0, Math.min(1, t));

        return interpolatePoint(p1, p2, t);
    }

    @Override
    public String toString() {
        return String.format("RouteGeometry[%d points, %.1f km]",
                coordinates.size(), calculateTotalDistance() / 1000.0);
    }
}