package biz.ugur.busroutebackend.integration.domain.events;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;


@Getter
public abstract class ExternalServiceDomainEvent implements DomainEvent {

    private final String eventId;
    private final String externalServiceId;
    private final Instant occurredAt;
    private final int eventVersion;

    protected ExternalServiceDomainEvent(String externalServiceId) {
        this.eventId = UUID.randomUUID().toString();
        this.externalServiceId = externalServiceId;
        this.occurredAt = Instant.now();
        this.eventVersion = getCurrentVersion();
    }

    protected ExternalServiceDomainEvent(String eventId, String externalServiceId, Instant occurredAt, int eventVersion) {
        this.eventId = eventId;
        this.externalServiceId = externalServiceId;
        this.occurredAt = occurredAt;
        this.eventVersion = eventVersion;
    }

    protected abstract int getCurrentVersion();

    @Override
    public String getEventType() {
        return this.getClass().getSimpleName();
    }
}
