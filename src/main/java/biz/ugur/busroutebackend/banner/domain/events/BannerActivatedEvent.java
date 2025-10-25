package biz.ugur.busroutebackend.banner.domain.events;

import lombok.Getter;

import java.time.Instant;


@Getter
public class BannerActivatedEvent extends BannerDomainEvent {

    public BannerActivatedEvent(String bannerId) {
        super(bannerId);
    }

    public BannerActivatedEvent(String eventId, String bannerId, Instant occurredAt) {
        super(eventId, bannerId, occurredAt);
    }

    @Override
    public String getEventType() {
        return "BannerActivatedEvent";
    }
}
