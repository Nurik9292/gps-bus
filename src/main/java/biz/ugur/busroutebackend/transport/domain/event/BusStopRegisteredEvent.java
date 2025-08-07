package biz.ugur.busroutebackend.transport.domain.event;

import biz.ugur.busroutebackend.shared.domain.DomainEvent;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;

import java.time.Instant;


public record BusStopRegisteredEvent(
        BusStopId stopId,
        String stopName,
        String stopCode,
        String createdBy,
        Instant occurredOn
) implements DomainEvent {

    public BusStopRegisteredEvent(BusStopId stopId, String stopName, String stopCode, String createdBy) {
        this(stopId, stopName, stopCode, createdBy, Instant.now());
    }

    @Override
    public Instant occurredOn() {
        return occurredOn;
    }
}