package biz.ugur.busroutebackend.transport.domain.event;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import lombok.Getter;

import java.time.Instant;

@Getter
public class VehicleRegisteredEvent implements DomainEvent {

    private final String vehicleId;
    private final String deviceId;
    private final String licensePlate;
    private final Instant eventOccurredAt;

    public VehicleRegisteredEvent(String vehicleId, String deviceId, String licensePlate) {
        this.vehicleId = vehicleId;
        this.deviceId = deviceId;
        this.licensePlate = licensePlate;
        this.eventOccurredAt = Instant.now();
    }

    @Override
    public Instant getOccurredAt() {
        return eventOccurredAt;
    }

    @Override
    public String toString() {
        return String.format("VehicleRegistered[id=%s, device=%s, plate=%s]",
                vehicleId, deviceId, licensePlate);
    }
}