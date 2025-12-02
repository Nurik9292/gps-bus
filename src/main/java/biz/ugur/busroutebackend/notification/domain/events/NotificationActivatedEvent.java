package biz.ugur.busroutebackend.notification.domain.events;

import lombok.Getter;

import java.time.Instant;


@Getter
public class NotificationActivatedEvent extends NotificationDomainEvent {

    public static final int CURRENT_VERSION = 1;


    public NotificationActivatedEvent(String notificationId) {
        super(notificationId);
    }

    public NotificationActivatedEvent(String eventId, String notificationId, Instant occurredAt, int eventVersion) {
        super(eventId, notificationId, occurredAt, eventVersion);
    }

    @Override
    protected int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public String getEventType() {
        return "NotificationActivatedEvent";
    }
}
