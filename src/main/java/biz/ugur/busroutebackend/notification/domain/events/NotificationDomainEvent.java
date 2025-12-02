package biz.ugur.busroutebackend.notification.domain.events;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;


@Getter
public abstract class NotificationDomainEvent implements DomainEvent {

    private final String eventId;
    private final String notificationId;
    private final Instant occurredAt;
    private final int eventVersion;


    protected NotificationDomainEvent(String notificationId) {
        this.eventId = UUID.randomUUID().toString();
        this.notificationId = notificationId;
        this.occurredAt = Instant.now();
        this.eventVersion = getCurrentVersion();
    }


    protected NotificationDomainEvent(String eventId, String notificationId, Instant occurredAt, int eventVersion) {
        this.eventId = eventId;
        this.notificationId = notificationId;
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
