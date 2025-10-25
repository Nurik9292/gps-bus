package biz.ugur.busroutebackend.banner.domain.events;

import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Getter
public class BannerUpdatedEvent extends BannerDomainEvent {

    private final Map<String, Object> changes;

    public BannerUpdatedEvent(String bannerId, Map<String, Object> changes) {
        super(bannerId);
        this.changes = new HashMap<>(changes);
    }

    public BannerUpdatedEvent(
            String eventId,
            String bannerId,
            Instant occurredAt,
            Map<String, Object> changes) {
        super(eventId, bannerId, occurredAt);
        this.changes = new HashMap<>(changes);
    }

    @Override
    public String getEventType() {
        return "BannerUpdatedEvent";
    }

    /**
     * Проверка, было ли изменено конкретное поле.
     */
    public boolean hasChange(String fieldName) {
        return changes.containsKey(fieldName);
    }

    /**
     * Получение значения изменения.
     */
    @SuppressWarnings("unchecked")
    public <T> T getChange(String fieldName, Class<T> type) {
        return (T) changes.get(fieldName);
    }
}
