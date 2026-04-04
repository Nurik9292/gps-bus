package biz.ugur.busroutebackend.transport.domain.event;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
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


    public BusStopLocationChangedEvent(BusStopId stopId, Coordinates coordinates) {
        this(stopId, coordinates.getLatitude(), coordinates.getLongitude(), Instant.now());
    }

    public Coordinates getCoordinates() {
        return Coordinates.of(newLatitude, newLongitude);
    }

    @Override
    public Instant occurredOn() {
        return occurredOn;
    }
}