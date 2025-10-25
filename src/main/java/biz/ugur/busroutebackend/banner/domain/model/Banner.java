package biz.ugur.busroutebackend.banner.domain.model;

import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.events.*;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerImage;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerPeriod;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerTitle;
import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Builder
@Getter
@EqualsAndHashCode(callSuper = false)
public class Banner extends AggregateRoot<Banner, BannerId> {

    private BannerId id;
    private BannerTitle title;
    private BannerType type;
    private BannerPeriod period;
    private BannerImage imageUrl;
    private String content;
    private String targetUrl;
    private Boolean isActive;
    private Integer displayOrder;

    public static Banner create(BannerTitle title,
                                BannerType type,
                                BannerPeriod period,
                                BannerImage imageUrl,
                                String targetUrl,
                                Integer displayOrder,
                                String content) {

        Banner banner = builder()
                .id(BannerId.generate())
                .title(title)
                .type(type)
                .period(period)
                .imageUrl(imageUrl)
                .targetUrl(targetUrl)
                .isActive(true)
                .displayOrder( displayOrder != null ? displayOrder : 0)
                .content(content)
                .build();

        // Регистрация события создания баннера для Event Sourcing
        banner.registerEvent(new BannerCreatedEvent(
                banner.id.getValue(),
                banner.title.getValue(),
                banner.type.getValue(),
                banner.imageUrl.getValue(),
                banner.targetUrl,
                banner.period.getStartTime(),
                banner.period.getEndTime(),
                banner.displayOrder,
                banner.content
        ));

        return banner;
    }

    public static Banner restore(BannerId id,
                                 BannerTitle title,
                                 BannerType type,
                                 BannerPeriod period,
                                 BannerImage imageUrl,
                                 String targetUrl,
                                 Boolean isActive,
                                 Integer displayOrder,
                                 String content,
                                 Instant createdAt,
                                 Instant updatedAt,
                                 Long version) {

        Banner banner = builder()
                .id(id)
                .title(title)
                .type(type)
                .imageUrl(imageUrl)
                .targetUrl(targetUrl)
                .isActive(isActive)
                .displayOrder(displayOrder)
                .period(period)
                .content(content)
                .build();

        banner.createdAt = createdAt;
        banner.updatedAt = updatedAt;
        banner.version = version != null ? version : 0L;

        return banner;
    }

    public void updateBanner(
            BannerTitle title,
            BannerType type,
            BannerPeriod  period,
            BannerImage imageUrl,
            String targetUrl,
            Integer displayOrder,
            String content) {

        if (title == null) {
            throw new IllegalArgumentException("Title cannot be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("Type cannot be null");
        }
        if (period == null) {
            throw new IllegalArgumentException("Period cannot be null");
        }
        if (imageUrl == null) {
            throw new IllegalArgumentException("Image URL cannot be null");
        }

        // Отслеживание изменений для BannerUpdatedEvent
        Map<String, Object> changes = new HashMap<>();

        if (!this.title.equals(title)) {
            changes.put("title", title.getValue());
            this.title = title;
        }

        if (!this.type.equals(type)) {
            changes.put("type", type.getValue());
            this.type = type;
        }

        if (!this.period.equals(period)) {
            changes.put("startDate", period.getStartTime());
            changes.put("endDate", period.getEndTime());
            this.period = period;
        }

        if (!this.imageUrl.equals(imageUrl)) {
            changes.put("imageUrl", imageUrl.getValue());
            this.imageUrl = imageUrl;
        }

        if (targetUrl != null && !targetUrl.trim().equals(this.targetUrl)) {
            changes.put("targetUrl", targetUrl.trim());
            this.targetUrl = targetUrl.trim();
        }

        if (displayOrder != null && !displayOrder.equals(this.displayOrder)) {
            changes.put("displayOrder", displayOrder);
            this.displayOrder = displayOrder;
        }

        if (content != null && !content.trim().equals(this.content)) {
            changes.put("content", content.trim());
            this.content = content.trim();
        }

        // Регистрация события обновления, если были изменения
        if (!changes.isEmpty()) {
            registerEvent(new BannerUpdatedEvent(this.id.getValue(), changes));
        }
    }

    public void deactivate() {
        if (Boolean.TRUE.equals(this.isActive)) {
            this.isActive = false;
            // Регистрация события деактивации для Event Sourcing
            registerEvent(new BannerDeactivatedEvent(this.id.getValue()));
        }
    }

    public void activate() {
        if (Boolean.FALSE.equals(this.isActive)) {
            this.isActive = true;
            // Регистрация события активации для Event Sourcing
            registerEvent(new BannerActivatedEvent(this.id.getValue()));
        }
    }

    @Override
    public BannerId getId() {
        return id;
    }

}
