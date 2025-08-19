package biz.ugur.busroutebackend.transport.domain.event;


import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import lombok.Getter;

import java.time.Instant;

@Getter
public class VehicleAssignedToRouteEvent implements DomainEvent {

    private final String vehicleId;
    private final String licensePlate;
    private final String previousRouteId;
    private final String newRouteId;
    private final Instant eventOccurredAt;

    public VehicleAssignedToRouteEvent(String vehicleId, String licensePlate,
                                       String previousRouteId, String newRouteId) {
        this.vehicleId = vehicleId;
        this.licensePlate = licensePlate;
        this.previousRouteId = previousRouteId;
        this.newRouteId = newRouteId;
        this.eventOccurredAt = Instant.now();
    }

    public boolean isAssignment() {
        return newRouteId != null;
    }

    public boolean isUnassignment() {
        return newRouteId == null;
    }

    public boolean isReassignment() {
        return previousRouteId != null && newRouteId != null;
    }

    @Override
    public Instant getOccurredAt() {
        return eventOccurredAt;
    }

    @Override
    public String toString() {
        if (isUnassignment()) {
            return String.format("VehicleUnassigned[vehicle=%s, plate=%s, from=%s]",
                    vehicleId, licensePlate, previousRouteId);
        } else if (isReassignment()) {
            return String.format("VehicleReassigned[vehicle=%s, plate=%s, from=%s, to=%s]",
                    vehicleId, licensePlate, previousRouteId, newRouteId);
        } else {
            return String.format("VehicleAssigned[vehicle=%s, plate=%s, to=%s]",
                    vehicleId, licensePlate, newRouteId);
        }
    }
}