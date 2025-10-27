package biz.ugur.busroutebackend.banner.domain.events;

import lombok.Getter;

import java.time.Instant;


@Getter
public class BannerActivatedEvent extends BannerDomainEvent {

    public static final int CURRENT_VERSION = 1;


    public BannerActivatedEvent(String bannerId) {
        super(bannerId);
    }

    public BannerActivatedEvent(String eventId, String bannerId, Instant occurredAt, int eventVersion) {
        super(eventId, bannerId, occurredAt, eventVersion);
    }

    @Override
    protected int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public String getEventType() {
        return "BannerActivatedEvent";
    }
}
