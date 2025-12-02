package biz.ugur.busroutebackend.notification.domain.events;

import lombok.Getter;

import java.time.Instant;


@Getter
public class NotificationCreatedEvent extends NotificationDomainEvent {

    public static final int CURRENT_VERSION = 1;

    private final String title;
    private final Integer displayOrder;
    private final String content;


    public NotificationCreatedEvent(
            String notificationId,
            String title,
            Integer displayOrder,
            String content) {
        super(notificationId);
        this.title = title;
        this.displayOrder = displayOrder;
        this.content = content;
    }


    public NotificationCreatedEvent(
            String eventId,
            String notificationId,
            Instant occurredAt,
            int eventVersion,
            String title,
            Integer displayOrder,
            String content) {
        super(eventId, notificationId, occurredAt, eventVersion);
        this.title = title;
        this.displayOrder = displayOrder;
        this.content = content;
    }

    @Override
    protected int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public String getEventType() {
        return "NotificationCreatedEvent";
    }
}
