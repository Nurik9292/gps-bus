package biz.ugur.busroutebackend.transport.domain.event;

import biz.ugur.busroutebackend.shared.domain.DomainEvent;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;

import java.math.BigDecimal;
import java.time.Instant;

public record BusStopLocationChangedEvent(
        BusStopId stopId,
        BigDecimal newLatitude,
        BigDecimal newLongitude,
        Instant occurredOn
) implements DomainEvent {

    public BusStopLocationChangedEvent(BusStopId stopId, BigDecimal newLatitude, BigDecimal newLongitude) {
        this(stopId, newLatitude, newLongitude, Instant.now());
    }

    @Override
    public Instant occurredOn() {
        return occurredOn;
    }
}