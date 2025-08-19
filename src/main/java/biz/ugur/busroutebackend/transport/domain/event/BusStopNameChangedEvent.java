package biz.ugur.busroutebackend.transport.domain.event;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;

import java.time.Instant;

public record BusStopNameChangedEvent(
        BusStopId stopId,
        String newName,
        String newNameEn,
        String newNameTm,
        Instant occurredOn
) implements DomainEvent {

    public BusStopNameChangedEvent(BusStopId stopId, String newName, String newNameEn, String newNameTm) {
        this(stopId, newName, newNameEn, newNameTm, Instant.now());
    }

    @Override
    public Instant occurredOn() {
        return occurredOn;
    }
}