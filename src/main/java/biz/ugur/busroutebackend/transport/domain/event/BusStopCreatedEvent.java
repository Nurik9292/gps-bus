package biz.ugur.busroutebackend.transport.domain.event;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import biz.ugur.busroutebackend.transport.domain.valueobject.StopCode;

import java.math.BigDecimal;
import java.time.Instant;

public record BusStopCreatedEvent(
        BusStopId stopId,
        String stopName,
        String nameEn,
        String nameTm,
        StopCode stopCode,
        BigDecimal latitude,
        BigDecimal longitude,
        Boolean isMajorStop,
        Instant occurredOn
) implements DomainEvent {

    public BusStopCreatedEvent(BusStopId stopId, String stopName, String nameEn, String nameTm,
                               StopCode stopCode, BigDecimal latitude, BigDecimal longitude, Boolean isMajorStop) {
        this(stopId, stopName, nameEn, nameTm, stopCode, latitude, longitude, isMajorStop, Instant.now());
    }

    @Override
    public Instant occurredOn() {
        return occurredOn;
    }
}
