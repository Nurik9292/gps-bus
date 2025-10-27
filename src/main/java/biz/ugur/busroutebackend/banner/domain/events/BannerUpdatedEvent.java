package biz.ugur.busroutebackend.banner.domain.events;

import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;


@Getter
public class BannerUpdatedEvent extends BannerDomainEvent {

    public static final int CURRENT_VERSION = 1;

    private final Map<String, Object> changes;


    public BannerUpdatedEvent(String bannerId, Map<String, Object> changes) {
        super(bannerId);
        this.changes = new HashMap<>(changes);
    }


    public BannerUpdatedEvent(
            String eventId,
            String bannerId,
            Instant occurredAt,
            int eventVersion,
            Map<String, Object> changes) {
        super(eventId, bannerId, occurredAt, eventVersion);
        this.changes = new HashMap<>(changes);
    }

    @Override
    protected int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public String getEventType() {
        return "BannerUpdatedEvent";
    }

    public boolean hasChange(String fieldName) {
        return changes.containsKey(fieldName);
    }

    @SuppressWarnings("unchecked")
    public <T> T getChange(String fieldName, Class<T> type) {
        return (T) changes.get(fieldName);
    }
}
