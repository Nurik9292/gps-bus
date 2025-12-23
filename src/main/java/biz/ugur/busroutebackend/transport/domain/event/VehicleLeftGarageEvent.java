package biz.ugur.busroutebackend.transport.domain.event;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;

@Getter
public class VehicleLeftGarageEvent implements DomainEvent {

    private final String vehicleId;
    private final String licensePlate;
    private final String garageId;
    private final String garageName;
    private final Double latitude;
    private final Double longitude;
    private final LocalDateTime exitTime;
    private final LocalDateTime entryTime;
    private final Long durationMinutes;
    private final String assignedRouteId;
    private final String assignedRouteNumber;
    private final Instant eventOccurredAt;

    public VehicleLeftGarageEvent(String vehicleId, String licensePlate,
                                  String garageId, String garageName,
                                  Double latitude, Double longitude,
                                  LocalDateTime exitTime, LocalDateTime entryTime,
                                  String assignedRouteId, String assignedRouteNumber) {
        this.vehicleId = vehicleId;
        this.licensePlate = licensePlate;
        this.garageId = garageId;
        this.garageName = garageName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.exitTime = exitTime;
        this.entryTime = entryTime;
        this.durationMinutes = calculateDurationMinutes(entryTime, exitTime);
        this.assignedRouteId = assignedRouteId;
        this.assignedRouteNumber = assignedRouteNumber;
        this.eventOccurredAt = Instant.now();
    }

    private Long calculateDurationMinutes(LocalDateTime entry, LocalDateTime exit) {
        if (entry == null || exit == null) {
            return null;
        }
        return Duration.between(entry, exit).toMinutes();
    }

    public boolean hasRouteAssignment() {
        return assignedRouteId != null;
    }

    @Override
    public Instant getOccurredAt() {
        return eventOccurredAt;
    }

    @Override
    public String toString() {
        String routeInfo = hasRouteAssignment()
            ? String.format(", route=%s", assignedRouteNumber)
            : ", no route";
        return String.format("VehicleLeftGarage[vehicle=%s, plate=%s, garage=%s (%s), duration=%d min%s]",
                vehicleId, licensePlate, garageId, garageName,
                durationMinutes != null ? durationMinutes : 0, routeInfo);
    }
}
