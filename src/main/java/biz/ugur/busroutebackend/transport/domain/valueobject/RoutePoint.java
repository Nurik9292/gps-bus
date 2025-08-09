package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;


@Getter
@EqualsAndHashCode(callSuper = false)
public class RoutePoint extends ValueObject {

    private final double longitude;
    private final double latitude;

    public RoutePoint(double longitude, double latitude) {
        validateCoordinates(longitude, latitude);
        this.longitude = longitude;
        this.latitude = latitude;
    }


    private void validateCoordinates(double longitude, double latitude) {
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException(
                    String.format("Invalid longitude: %.6f. Must be between -180 and 180", longitude)
            );
        }

        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException(
                    String.format("Invalid latitude: %.6f. Must be between -90 and 90", latitude)
            );
        }
    }


    public boolean isWithinTurkmenistan() {
        return longitude >= 52.0 && longitude <= 67.0 &&
                latitude >= 35.0 && latitude <= 43.0;
    }


    public boolean isValid() {
        return longitude >= -180.0 && longitude <= 180.0 &&
                latitude >= -90.0 && latitude <= 90.0;
    }


    public static RoutePoint fromArray(double[] coordinates) {
        if (coordinates == null || coordinates.length != 2) {
            throw new IllegalArgumentException("Coordinates array must contain exactly 2 elements [longitude, latitude]");
        }
        return new RoutePoint(coordinates[0], coordinates[1]);
    }


    public double[] toArray() {
        return new double[]{longitude, latitude};
    }


    public String toWKT() {
        return String.format("POINT(%.6f %.6f)", longitude, latitude);
    }


    public double distanceTo(RoutePoint other) {
        final double R = 6371000; // Радиус Земли в метрах

        double lat1Rad = Math.toRadians(this.latitude);
        double lat2Rad = Math.toRadians(other.latitude);
        double deltaLatRad = Math.toRadians(other.latitude - this.latitude);
        double deltaLonRad = Math.toRadians(other.longitude - this.longitude);

        double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                        Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }

    @Override
    public String toString() {
        return String.format("RoutePoint{lng=%.6f, lat=%.6f}", longitude, latitude);
    }
}