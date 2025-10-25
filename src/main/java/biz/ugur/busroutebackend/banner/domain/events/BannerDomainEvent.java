package biz.ugur.busroutebackend.banner.domain.events;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;


@Getter
public abstract class BannerDomainEvent implements DomainEvent {


    private final String eventId;
    private final String bannerId;
    private final Instant occurredAt;

    protected BannerDomainEvent(String bannerId) {
        this.eventId = UUID.randomUUID().toString();
        this.bannerId = bannerId;
        this.occurredAt = Instant.now();
    }

    protected BannerDomainEvent(String eventId, String bannerId, Instant occurredAt) {
        this.eventId = eventId;
        this.bannerId = bannerId;
        this.occurredAt = occurredAt;
    }

    @Override
    public String getEventId() {
        return eventId;
    }

    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }
}
