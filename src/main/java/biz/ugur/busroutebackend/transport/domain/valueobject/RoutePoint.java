package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class RoutePoint extends ValueObject {

    private final Double latitude;
    private final Double longitude;

    public RoutePoint(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("Coordinates cannot be null");
        }
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void validate() {
        if (latitude < 35.0 || latitude > 43.0) {
            throw new IllegalArgumentException("Latitude outside Turkmenistan bounds: " + latitude);
        }
        if (longitude < 52.0 || longitude > 67.0) {
            throw new IllegalArgumentException("Longitude outside Turkmenistan bounds: " + longitude);
        }
    }

    public double distanceTo(RoutePoint other) {
        return distanceTo(other.latitude, other.longitude);
    }

    public double distanceTo(Double lat, Double lon) {
        final int R = 6371000; // Радиус Земли в метрах

        double lat1Rad = Math.toRadians(this.latitude);
        double lat2Rad = Math.toRadians(lat);
        double deltaLatRad = Math.toRadians(lat - this.latitude);
        double deltaLonRad = Math.toRadians(lon - this.longitude);

        double a = Math.sin(deltaLatRad/2) * Math.sin(deltaLatRad/2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(deltaLonRad/2) * Math.sin(deltaLonRad/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));

        return R * c;
    }

    @Override
    public String toString() {
        return String.format("(%.6f, %.6f)", latitude, longitude);
    }
}