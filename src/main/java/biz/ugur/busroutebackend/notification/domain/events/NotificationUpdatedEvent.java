package biz.ugur.busroutebackend.notification.domain.events;

import lombok.Getter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;


@Getter
public class NotificationUpdatedEvent extends NotificationDomainEvent {

    public static final int CURRENT_VERSION = 1;

    private final Map<String, Object> changes;


    public NotificationUpdatedEvent(String notificationId, Map<String, Object> changes) {
        super(notificationId);
        this.changes = new HashMap<>(changes);
    }


    public NotificationUpdatedEvent(
            String eventId,
            String notificationId,
            Instant occurredAt,
            int eventVersion,
            Map<String, Object> changes) {
        super(eventId, notificationId, occurredAt, eventVersion);
        this.changes = new HashMap<>(changes);
    }

    @Override
    protected int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public String getEventType() {
        return "NotificationUpdatedEvent";
    }

    public boolean hasChange(String fieldName) {
        return changes.containsKey(fieldName);
    }

    @SuppressWarnings("unchecked")
    public <T> T getChange(String fieldName, Class<T> type) {
        return (T) changes.get(fieldName);
    }
}
