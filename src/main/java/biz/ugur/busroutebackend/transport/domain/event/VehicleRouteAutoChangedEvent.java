package biz.ugur.busroutebackend.transport.domain.event;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteSource;
import lombok.Getter;

import java.time.Instant;

@Getter
public class VehicleRouteAutoChangedEvent implements DomainEvent {

    private final String vehicleId;
    private final String licensePlate;
    private final String previousRouteId;
    private final String previousRouteNumber;
    private final String newRouteId;
    private final String newRouteNumber;
    private final RouteSource source;
    private final Integer confidence;
    private final String reason;
    private final Instant eventOccurredAt;

    public VehicleRouteAutoChangedEvent(String vehicleId, String licensePlate,
                                        String previousRouteId, String previousRouteNumber,
                                        String newRouteId, String newRouteNumber,
                                        RouteSource source, Integer confidence, String reason) {
        this.vehicleId = vehicleId;
        this.licensePlate = licensePlate;
        this.previousRouteId = previousRouteId;
        this.previousRouteNumber = previousRouteNumber;
        this.newRouteId = newRouteId;
        this.newRouteNumber = newRouteNumber;
        this.source = source;
        this.confidence = confidence;
        this.reason = reason;
        this.eventOccurredAt = Instant.now();
    }

    public boolean isRouteChange() {
        return previousRouteId != null && newRouteId != null
            && !previousRouteId.equals(newRouteId);
    }

    public boolean isNewAssignment() {
        return previousRouteId == null && newRouteId != null;
    }

    @Override
    public Instant getOccurredAt() {
        return eventOccurredAt;
    }

    @Override
    public String toString() {
        String changeType = isNewAssignment() ? "assigned" : "changed";
        return String.format("VehicleRouteAuto%s[vehicle=%s, plate=%s, %s->%s, source=%s, confidence=%d%%, reason=%s]",
                changeType.substring(0, 1).toUpperCase() + changeType.substring(1),
                vehicleId, licensePlate,
                previousRouteNumber != null ? previousRouteNumber : "none",
                newRouteNumber, source, confidence, reason);
    }
}
