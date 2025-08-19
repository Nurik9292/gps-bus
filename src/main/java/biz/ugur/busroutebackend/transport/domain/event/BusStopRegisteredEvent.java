package biz.ugur.busroutebackend.transport.domain.event;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import biz.ugur.busroutebackend.transport.domain.valueobject.StopCode;

import java.time.Instant;


public record BusStopRegisteredEvent(
        BusStopId stopId,
        String stopName,
        StopCode stopCode,
        String createdBy,
        Instant occurredOn
) implements DomainEvent {

    public BusStopRegisteredEvent(BusStopId stopId, String stopName, StopCode stopCode, String createdBy) {
        this(stopId, stopName, stopCode, createdBy, Instant.now());
    }

    @Override
    public Instant occurredOn() {
        return occurredOn;
    }
}