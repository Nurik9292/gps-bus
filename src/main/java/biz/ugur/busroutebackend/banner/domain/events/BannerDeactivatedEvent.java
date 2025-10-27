package biz.ugur.busroutebackend.banner.domain.events;

import lombok.Getter;

import java.time.Instant;


@Getter
public class BannerDeactivatedEvent extends BannerDomainEvent {

    public static final int CURRENT_VERSION = 1;


    public BannerDeactivatedEvent(String bannerId) {
        super(bannerId);
    }


    public BannerDeactivatedEvent(String eventId, String bannerId, Instant occurredAt, int eventVersion) {
        super(eventId, bannerId, occurredAt, eventVersion);
    }

    @Override
    protected int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public String getEventType() {
        return "BannerDeactivatedEvent";
    }
}
