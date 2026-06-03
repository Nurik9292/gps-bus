package biz.ugur.busroutebackend.subscription.domain.events;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public abstract class SubscriptionDomainEvent implements DomainEvent {

    private final String eventId;
    private final String subscriptionId;
    private final Instant occurredAt;
    private final int eventVersion;

    protected SubscriptionDomainEvent(String subscriptionId) {
        this.eventId = UUID.randomUUID().toString();
        this.subscriptionId = subscriptionId;
        this.occurredAt = Instant.now();
        this.eventVersion = getCurrentVersion();
    }

    @Override public String getEventId() { return eventId; }
    @Override public Instant getOccurredAt() { return occurredAt; }

    protected abstract int getCurrentVersion();
}
