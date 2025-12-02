package biz.ugur.busroutebackend.notification.domain.events;

import lombok.Getter;

import java.time.Instant;


@Getter
public class NotificationDeletedEvent extends NotificationDomainEvent {

    public static final int CURRENT_VERSION = 1;


    public NotificationDeletedEvent(String notificationId) {
        super(notificationId);
    }


    public NotificationDeletedEvent(String eventId, String notificationId, Instant occurredAt, int eventVersion) {
        super(eventId, notificationId, occurredAt, eventVersion);
    }

    @Override
    protected int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public String getEventType() {
        return "NotificationDeletedEvent";
    }
}
