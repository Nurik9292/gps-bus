package biz.ugur.busroutebackend.transport.domain.event;

import biz.ugur.busroutebackend.shared.domain.DomainEvent;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;

import java.time.Instant;

public record BusStopUpdatedEvent(
        BusStopId stopId,
        String stopName,
        Instant occurredOn
) implements DomainEvent {

    public BusStopUpdatedEvent(BusStopId stopId, String stopName) {
        this(stopId, stopName, Instant.now());
    }

    @Override
    public Instant occurredOn() {
        return occurredOn;
    }
}