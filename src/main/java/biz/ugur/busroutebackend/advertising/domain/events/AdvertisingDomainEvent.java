package biz.ugur.busroutebackend.advertising.domain.events;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public abstract class AdvertisingDomainEvent implements DomainEvent {

    private final String eventId;
    private final String aggregateId;
    private final Instant occurredAt;
    private final int eventVersion;

    protected AdvertisingDomainEvent(String aggregateId) {
        this.eventId = UUID.randomUUID().toString();
        this.aggregateId = aggregateId;
        this.occurredAt = Instant.now();
        this.eventVersion = getCurrentVersion();
    }

    @Override public String getEventId() { return eventId; }
    @Override public Instant getOccurredAt() { return occurredAt; }

    protected abstract int getCurrentVersion();
}
