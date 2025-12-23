package biz.ugur.busroutebackend.transport.domain.event;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;

@Getter
public class VehicleEnteredGarageEvent implements DomainEvent {

    private final String vehicleId;
    private final String licensePlate;
    private final String garageId;
    private final String garageName;
    private final Double latitude;
    private final Double longitude;
    private final LocalDateTime entryTime;
    private final Instant eventOccurredAt;

    public VehicleEnteredGarageEvent(String vehicleId, String licensePlate,
                                     String garageId, String garageName,
                                     Double latitude, Double longitude,
                                     LocalDateTime entryTime) {
        this.vehicleId = vehicleId;
        this.licensePlate = licensePlate;
        this.garageId = garageId;
        this.garageName = garageName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.entryTime = entryTime;
        this.eventOccurredAt = Instant.now();
    }

    @Override
    public Instant getOccurredAt() {
        return eventOccurredAt;
    }

    @Override
    public String toString() {
        return String.format("VehicleEnteredGarage[vehicle=%s, plate=%s, garage=%s (%s), at=%s]",
                vehicleId, licensePlate, garageId, garageName, entryTime);
    }
}
