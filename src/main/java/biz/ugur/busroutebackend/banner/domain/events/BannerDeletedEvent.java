package biz.ugur.busroutebackend.banner.domain.events;

import lombok.Getter;

import java.time.Instant;


@Getter
public class BannerDeletedEvent extends BannerDomainEvent {

    public static final int CURRENT_VERSION = 1;

    private final String reason;


    public BannerDeletedEvent(String bannerId) {
        this(bannerId, null);
    }


    public BannerDeletedEvent(String bannerId, String reason) {
        super(bannerId);
        this.reason = reason;
    }

    public BannerDeletedEvent(String eventId, String bannerId, Instant occurredAt, int eventVersion, String reason) {
        super(eventId, bannerId, occurredAt, eventVersion);
        this.reason = reason;
    }

    @Override
    protected int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public String getEventType() {
        return "BannerDeletedEvent";
    }
}
