package biz.ugur.busroutebackend.payment.domain.events;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public abstract class PaymentDomainEvent implements DomainEvent {

    private final String eventId;
    private final String paymentId;
    private final Instant occurredAt;
    private final int eventVersion;

    protected PaymentDomainEvent(String paymentId) {
        this.eventId = UUID.randomUUID().toString();
        this.paymentId = paymentId;
        this.occurredAt = Instant.now();
        this.eventVersion = getCurrentVersion();
    }

    @Override public String getEventId() { return eventId; }
    @Override public Instant getOccurredAt() { return occurredAt; }

    protected abstract int getCurrentVersion();
}
