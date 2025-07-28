package biz.ugur.busroutebackend.transport.domain.event;

import biz.ugur.busroutebackend.shared.domain.DomainEvent;
import lombok.Getter;

import java.time.Instant;

@Getter
public class VehiclePositionUpdatedEvent implements DomainEvent {

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

    public VehiclePositionUpdatedEvent(String vehicleId, String deviceId, String licensePlate,
                                       String routeNumber,
                                       Double latitude, Double longitude, Double speedKmh,
                                       Boolean isInMotion, Instant positionTimestamp) {
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
    }

    @Override
    public Instant getOccurredAt() {
        return eventOccurredAt;
    }

    @Override
    public String toString() {
        return String.format("VehiclePositionUpdated[vehicle=%s, plate=%s, position=(%.6f,%.6f), speed=%.1f, routeNumber=%s]",
                vehicleId, licensePlate, latitude, longitude, speedKmh, routeNumber);
    }
}
