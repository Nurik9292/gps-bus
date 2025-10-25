package biz.ugur.busroutebackend.banner.domain.events;

import lombok.Getter;

import java.time.Instant;


@Getter
public class BannerDeactivatedEvent extends BannerDomainEvent {

    public BannerDeactivatedEvent(String bannerId) {
        super(bannerId);
    }

    public BannerDeactivatedEvent(String eventId, String bannerId, Instant occurredAt) {
        super(eventId, bannerId, occurredAt);
    }

    @Override
    public String getEventType() {
        return "BannerDeactivatedEvent";
    }
}
