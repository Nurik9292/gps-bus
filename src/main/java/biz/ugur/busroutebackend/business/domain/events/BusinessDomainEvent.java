package biz.ugur.busroutebackend.business.domain.events;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public abstract class BusinessDomainEvent implements DomainEvent {

    private final String eventId;
    private final String businessId;
    private final Instant occurredAt;
    private final int eventVersion;

    protected BusinessDomainEvent(String businessId) {
        this.eventId = UUID.randomUUID().toString();
        this.businessId = businessId;
        this.occurredAt = Instant.now();
        this.eventVersion = getCurrentVersion();
    }

    protected BusinessDomainEvent(String eventId, String businessId, Instant occurredAt, int eventVersion) {
        this.eventId = eventId;
        this.businessId = businessId;
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
