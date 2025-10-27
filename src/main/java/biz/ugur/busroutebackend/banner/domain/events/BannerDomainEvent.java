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
    private final int eventVersion;


    protected BannerDomainEvent(String bannerId) {
        this.eventId = UUID.randomUUID().toString();
        this.bannerId = bannerId;
        this.occurredAt = Instant.now();
        this.eventVersion = getCurrentVersion();
    }


    protected BannerDomainEvent(String eventId, String bannerId, Instant occurredAt, int eventVersion) {
        this.eventId = eventId;
        this.bannerId = bannerId;
        this.occurredAt = occurredAt;
        this.eventVersion = eventVersion;
    }

    @Override
    public String getEventId() {
        return eventId;
    }

    @Override
    public Instant getOccurredAt() {
        return occurredAt;
    }


    protected abstract int getCurrentVersion();
}
