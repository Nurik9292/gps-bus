package biz.ugur.busroutebackend.admin.domain.events;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public abstract class AdminDomainEvent implements DomainEvent {

    private final String eventId;
    private final String adminId;
    private final Instant occurredAt;
    private final int eventVersion;

    protected AdminDomainEvent(String adminId) {
        this.eventId = UUID.randomUUID().toString();
        this.adminId = adminId;
        this.occurredAt = Instant.now();
        this.eventVersion = getCurrentVersion();
    }

    protected AdminDomainEvent(String eventId, String adminId, Instant occurredAt, int eventVersion) {
        this.eventId = eventId;
        this.adminId = adminId;
        this.occurredAt = occurredAt;
        this.eventVersion = eventVersion;
    }

    protected abstract int getCurrentVersion();

    @Override
    public String getEventType() {
        return this.getClass().getSimpleName();
    }
}
