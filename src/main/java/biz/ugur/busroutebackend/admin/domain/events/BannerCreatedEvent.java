package biz.ugur.busroutebackend.admin.domain.events;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import lombok.Getter;

import java.time.Instant;

@Getter
public class BannerCreatedEvent implements DomainEvent {

    private final String bannerId;
    private final String title;
    private final String type;
    private final String imageUrl;
    private final Instant eventOccurredAt;

    public BannerCreatedEvent(String bannerId, String title, String type, String imageUrl) {
        this.bannerId = bannerId;
        this.title = title;
        this.type = type;
        this.imageUrl = imageUrl;
        this.eventOccurredAt = Instant.now();
    }

    @Override
    public Instant getOccurredAt() {
        return eventOccurredAt;
    }

    @Override
    public String getEventType() {
        return "BannerCreated";
    }



    @Override
    public String toString() {
        return String.format("BannerCreated[id=%s, title=%s]", bannerId, title);
    }
}
