package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class VehiclePosition extends ValueObject {

    private final Double latitude;
    private final Double longitude;
    private final Double speedKmh;
    private final Boolean isInMotion;

    public VehiclePosition(Double latitude, Double longitude, Double speedKmh, Boolean isInMotion) {
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("Coordinates cannot be null");
        }
        this.latitude = latitude;
        this.longitude = longitude;
        this.speedKmh = speedKmh != null ? speedKmh : 0.0;
        this.isInMotion = isInMotion != null ? isInMotion : false;
    }

    public double distanceTo(VehiclePosition other) {
        return distanceTo(other.latitude, other.longitude);
    }

    public double distanceTo(Double lat, Double lon) {
        final int R = 6371000;

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
        return String.format("Position(%.6f, %.6f, %.1f km/h, moving: %s)",
                latitude, longitude, speedKmh, isInMotion);
    }
}