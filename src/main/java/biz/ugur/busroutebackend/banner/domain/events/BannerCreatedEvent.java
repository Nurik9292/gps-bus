package biz.ugur.busroutebackend.banner.domain.events;

import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;


@Getter
public class BannerCreatedEvent extends BannerDomainEvent {

    public static final int CURRENT_VERSION = 2;

    private final String title;
    private final String type;
    private final String imageUrl;
    private final String targetUrl;
    private final LocalDateTime startDate;
    private final LocalDateTime endDate;
    private final Integer displayOrder;
    private final String content;
    private final Integer replyTime;


    public BannerCreatedEvent(
            String bannerId,
            String title,
            String type,
            String imageUrl,
            String targetUrl,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Integer displayOrder,
            String content,
            Integer replyTime) {
        super(bannerId);
        this.title = title;
        this.type = type;
        this.imageUrl = imageUrl;
        this.targetUrl = targetUrl;
        this.startDate = startDate;
        this.endDate = endDate;
        this.displayOrder = displayOrder;
        this.content = content;
        this.replyTime = replyTime;
    }


    public BannerCreatedEvent(
            String eventId,
            String bannerId,
            Instant occurredAt,
            int eventVersion,
            String title,
            String type,
            String imageUrl,
            String targetUrl,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Integer displayOrder,
            String content,
            Integer replyTime) {
        super(eventId, bannerId, occurredAt, eventVersion);
        this.title = title;
        this.type = type;
        this.imageUrl = imageUrl;
        this.targetUrl = targetUrl;
        this.startDate = startDate;
        this.endDate = endDate;
        this.displayOrder = displayOrder;
        this.content = content;
        this.replyTime = replyTime;
    }

    @Override
    protected int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public String getEventType() {
        return "BannerCreatedEvent";
    }
}
