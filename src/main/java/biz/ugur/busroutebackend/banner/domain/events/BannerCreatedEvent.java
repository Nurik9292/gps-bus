package biz.ugur.busroutebackend.banner.domain.events;

import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;


@Getter
public class BannerCreatedEvent extends BannerDomainEvent {

    private final String title;
    private final String type;
    private final String imageUrl;
    private final String targetUrl;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final Integer displayOrder;
    private final String content;

    public BannerCreatedEvent(
            String bannerId,
            String title,
            String type,
            String imageUrl,
            String targetUrl,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Integer displayOrder,
            String content) {
        super(bannerId);
        this.title = title;
        this.type = type;
        this.imageUrl = imageUrl;
        this.targetUrl = targetUrl;
        this.startDate = startDate;
        this.endDate = endDate;
        this.displayOrder = displayOrder;
        this.content = content;
    }

    public BannerCreatedEvent(
            String eventId,
            String bannerId,
            Instant occurredAt,
            String title,
            String type,
            String imageUrl,
            String targetUrl,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Integer displayOrder,
            String content) {
        super(eventId, bannerId, occurredAt);
        this.title = title;
        this.type = type;
        this.imageUrl = imageUrl;
        this.targetUrl = targetUrl;
        this.startDate = startDate;
        this.endDate = endDate;
        this.displayOrder = displayOrder;
        this.content = content;
    }

    @Override
    public String getEventType() {
        return "BannerCreatedEvent";
    }
}
