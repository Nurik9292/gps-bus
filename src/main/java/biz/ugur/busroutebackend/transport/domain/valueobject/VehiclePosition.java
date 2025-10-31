package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;


@Getter
@EqualsAndHashCode(callSuper = false)
public class VehiclePosition extends ValueObject {

    private final Coordinates coordinates;
    private final Double speedKmh;
    private final Boolean isInMotion;

    public VehiclePosition(Double latitude, Double longitude, Double speedKmh, Boolean isInMotion) {
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException("Coordinates cannot be null");
        }
        this.coordinates = Coordinates.of(latitude, longitude);
        this.speedKmh = speedKmh != null ? speedKmh : 0.0;
        this.isInMotion = isInMotion != null ? isInMotion : false;
    }


    public static VehiclePosition of(Coordinates coordinates, Double speedKmh, Boolean isInMotion) {
        if (coordinates == null) {
            throw new IllegalArgumentException("Coordinates cannot be null");
        }
        return new VehiclePosition(
                coordinates.getLatitudeAsDouble(),
                coordinates.getLongitudeAsDouble(),
                speedKmh,
                isInMotion
        );
    }


    public Double getLatitude() {
        return coordinates.getLatitudeAsDouble();
    }


    public Double getLongitude() {
        return coordinates.getLongitudeAsDouble();
    }

    @Override
    public String toString() {
        return String.format("VehiclePosition(%.6f, %.6f, %.1f km/h, moving: %s)",
                getLatitude(), getLongitude(), speedKmh, isInMotion);
    }
}
