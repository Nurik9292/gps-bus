package biz.ugur.busroutebackend.transport.domain.event;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

@ToString
@Getter
public class VehiclePositionUpdatedEvent implements DomainEvent{

    private final String vehicleId;
    private final String deviceId;
    private final String licensePlate;
    private final String routeNumber;
    private final Double latitude;
    private final Double longitude;
    private final Double speedKmh;
    private final Boolean isInMotion;
    private final Instant positionTimestamp;
    private final Instant eventOccurredAt;
    private final Double course;

    public VehiclePositionUpdatedEvent(String vehicleId, String deviceId, String licensePlate,
                                       String routeNumber,
                                       Double latitude, Double longitude, Double speedKmh,
                                       Boolean isInMotion, Instant positionTimestamp, Double course) {
        this.vehicleId = vehicleId;
        this.deviceId = deviceId;
        this.licensePlate = licensePlate;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speedKmh = speedKmh;
        this.isInMotion = isInMotion;
        this.positionTimestamp = positionTimestamp;
        this.eventOccurredAt = Instant.now();
        this.routeNumber = routeNumber;
        this.course = course;
    }

    /**
     * Convenience constructor using Coordinates value object
     */
    public VehiclePositionUpdatedEvent(String vehicleId, String deviceId, String licensePlate,
                                       String routeNumber, Coordinates coordinates, Double speedKmh,
                                       Boolean isInMotion, Instant positionTimestamp, Double course) {
        this(vehicleId, deviceId, licensePlate, routeNumber,
                coordinates.getLatitudeAsDouble(), coordinates.getLongitudeAsDouble(),
                speedKmh, isInMotion, positionTimestamp, course);
    }

    /**
     * Get coordinates as value object
     */
    public Coordinates getCoordinates() {
        return Coordinates.of(latitude, longitude);
    }

    @Override
    public Instant getOccurredAt() {
        return eventOccurredAt;
    }


}
