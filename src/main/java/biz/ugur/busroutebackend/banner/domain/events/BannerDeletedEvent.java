package biz.ugur.busroutebackend.banner.domain.events;

import lombok.Getter;

import java.time.Instant;


@Getter
public class BannerDeletedEvent extends BannerDomainEvent {

    private final String reason;

    public BannerDeletedEvent(String bannerId) {
        this(bannerId, null);
    }

    public BannerDeletedEvent(String bannerId, String reason) {
        super(bannerId);
        this.reason = reason;
    }

    public BannerDeletedEvent(String eventId, String bannerId, Instant occurredAt, String reason) {
        super(eventId, bannerId, occurredAt);
        this.reason = reason;
    }

    @Override
    public String getEventType() {
        return "BannerDeletedEvent";
    }
}
