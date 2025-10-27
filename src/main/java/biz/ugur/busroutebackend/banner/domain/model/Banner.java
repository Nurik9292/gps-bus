package biz.ugur.busroutebackend.banner.domain.model;

import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.banner.domain.events.*;
import biz.ugur.busroutebackend.banner.domain.exceptions.BannerValidationException;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerImage;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerPeriod;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerTitle;
import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Builder(toBuilder = true)
@Getter
@EqualsAndHashCode(callSuper = false)
public class Banner extends AggregateRoot<Banner, BannerId> {

    private final BannerId id;
    private final BannerTitle title;
    private final BannerType type;
    private final BannerPeriod period;
    private final BannerImage imageUrl;
    private final String content;
    private final String targetUrl;
    private final Boolean isActive;
    private final Integer displayOrder;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

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
                .version(0L)
                .build();

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
                                 LocalDateTime createdAt,
                                 LocalDateTime updatedAt,
                                 Long version) {

        return builder()
                .id(id)
                .title(title)
                .type(type)
                .imageUrl(imageUrl)
                .targetUrl(targetUrl)
                .isActive(isActive)
                .displayOrder(displayOrder)
                .period(period)
                .content(content)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version != null ? version : 0L)
                .build();
    }


    public Banner updateBanner(
            BannerTitle title,
            BannerType type,
            BannerPeriod  period,
            BannerImage imageUrl,
            String targetUrl,
            Integer displayOrder,
            String content) {

        if (title == null) {
            throw new BannerValidationException("Title cannot be null");
        }
        if (type == null) {
            throw new BannerValidationException("Type cannot be null");
        }
        if (period == null) {
            throw new BannerValidationException("Period cannot be null");
        }
        if (imageUrl == null) {
            throw new BannerValidationException("Image URL cannot be null");
        }

        Map<String, Object> changes = new HashMap<>();

        if (!this.title.equals(title)) {
            changes.put("title", title.getValue());
        }
        if (!this.type.equals(type)) {
            changes.put("type", type.getValue());
        }
        if (!this.period.equals(period)) {
            changes.put("startDate", period.getStartTime());
            changes.put("endDate", period.getEndTime());
        }
        if (!this.imageUrl.equals(imageUrl)) {
            changes.put("imageUrl", imageUrl.getValue());
        }

        String normalizedTargetUrl = targetUrl != null ? targetUrl.trim() : this.targetUrl;
        if (!normalizedTargetUrl.equals(this.targetUrl)) {
            changes.put("targetUrl", normalizedTargetUrl);
        }

        Integer finalDisplayOrder = displayOrder != null ? displayOrder : this.displayOrder;
        if (!finalDisplayOrder.equals(this.displayOrder)) {
            changes.put("displayOrder", finalDisplayOrder);
        }

        String normalizedContent = content != null ? content.trim() : this.content;
        if (!normalizedContent.equals(this.content)) {
            changes.put("content", normalizedContent);
        }

        // Create new instance with updated values
        Banner updatedBanner = this.toBuilder()
                .title(title)
                .type(type)
                .period(period)
                .imageUrl(imageUrl)
                .targetUrl(normalizedTargetUrl)
                .displayOrder(finalDisplayOrder)
                .content(normalizedContent)
                .build();

        if (!changes.isEmpty()) {
            updatedBanner.registerEvent(new BannerUpdatedEvent(this.id.getValue(), changes));
        }

        return updatedBanner;
    }


    public Banner deactivate() {
        if (Boolean.FALSE.equals(this.isActive)) {
            return this;
        }

        Banner deactivatedBanner = this.toBuilder()
                .isActive(false)
                .build();

        deactivatedBanner.registerEvent(new BannerDeactivatedEvent(this.id.getValue()));
        return deactivatedBanner;
    }

    public Banner activate() {
        if (Boolean.TRUE.equals(this.isActive)) {
            return this;
        }

        Banner activatedBanner = this.toBuilder()
                .isActive(true)
                .build();

        activatedBanner.registerEvent(new BannerActivatedEvent(this.id.getValue()));
        return activatedBanner;
    }

    @Override
    public BannerId getId() {
        return id;
    }

    @Override
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public Long getVersion() {
        return version;
    }

    @Override
    public void setVersion(Long version) {
        this.version = version;
    }

}
