package biz.ugur.busroutebackend.banner.domain.model;

import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import biz.ugur.busroutebackend.admin.domain.events.BannerCreatedEvent;
import biz.ugur.busroutebackend.banner.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

@Builder
@Getter
@Table("banners")
public class Banner extends AggregateRoot<Banner, BannerId> {

    @Id
    @Column("id")
    private BannerId id;

    @Column("title")
    private String title;

    @Column("type")
    private BannerType type;

    @Setter
    @Column("image_url")
    private String imageUrl;

    @Column("target_url")
    private String targetUrl;

    @Column("is_active")
    private Boolean isActive;

    @Column("display_order")
    private Integer displayOrder;

    @Setter
    @Column("start_date")
    private LocalDateTime startDate;

    @Setter
    @Column("end_date")
    private LocalDateTime endDate;

    @Column("content")
    private String content;

    public static Banner create(String title,
                                BannerType type,
                                String imageUrl,
                                String targetUrl,
                                Integer displayOrder,
                                String content) {
        Banner banner = builder()
                .id(BannerId.generate())
                .title(title)
                .type(type)
                .imageUrl(imageUrl)
                .targetUrl(targetUrl)
                .isActive(true)
                .displayOrder( displayOrder != null ? displayOrder : 0)
                .startDate(LocalDateTime.now())
                .endDate(null)
                .content(content)
                .build();

        banner.registerEvent(new BannerCreatedEvent(
                banner.id.getValue(),
                banner.title,
                banner.type.getValue(),
                banner.imageUrl
        ));

        return banner;
    }

    public static Banner restore(BannerId id,
                                 String title,
                                 BannerType type,
                                 String imageUrl,
                                 String targetUrl,
                                 Boolean isActive,
                                 Integer displayOrder,
                                 LocalDateTime startDate,
                                 LocalDateTime endDate,
                                 Instant createdAt,
                                 Instant updatedAt,
                                 String content,
                                 Long version) {
        Banner banner = builder()
                .id(id)
                .title(title)
                .type(type)
                .imageUrl(imageUrl)
                .targetUrl(targetUrl)
                .isActive(isActive)
                .displayOrder(displayOrder)
                .startDate(startDate)
                .endDate(endDate)
                .content(content)
                .build();

        banner.createdAt = createdAt;
        banner.updatedAt = updatedAt;
        banner.version = version != null ? version : 0L;

        return banner;
    }

    public void updateBanner(
            String title,
            BannerType type,
            String imageUrl,
            String targetUrl,
            Integer displayOrder,
            String content) {
        if (title != null && !title.trim().isEmpty()) {
            this.title = title.trim();
        }

        if (type != null) {
            this.type = type;
        }

        if (targetUrl != null) {
            this.targetUrl = targetUrl.trim();
        }

        if(imageUrl != null) {
            this.imageUrl = imageUrl.trim();
        }

        if (displayOrder != null) {
            this.displayOrder = displayOrder;
        }

        if(content != null) {
            this.content = content.trim();
        }

    }

    public void deactivate() {
        this.isActive = false;
    }

    public void activate() {
        this.isActive = true;
    }

    @Override
    public BannerId getId() {
        return id;
    }

    private String validateTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Banner title cannot be null or empty");
        }
        return title.trim();
    }

    private String validateImageUrl(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Banner image URL cannot be null or empty");
        }
        return imageUrl.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Banner banner)) return false;
        if (!super.equals(o)) return false;
        return Objects.equals(id, banner.id) &&
                Objects.equals(title, banner.title) &&
                Objects.equals(type, banner.type) &&
                Objects.equals(imageUrl, banner.imageUrl) &&
                Objects.equals(targetUrl, banner.targetUrl) &&
                Objects.equals(isActive, banner.isActive) &&
                Objects.equals(displayOrder, banner.displayOrder) &&
                Objects.equals(startDate, banner.startDate) &&
                Objects.equals(content, banner.content) &&
                Objects.equals(endDate, banner.endDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), id, title, type, imageUrl, targetUrl, isActive, displayOrder, startDate, content, endDate);
    }

    @Override
    public String toString() {
        return "Banner{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", type='" + type + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", targetUrl='" + targetUrl + '\'' +
                ", isActive=" + isActive +
                ", displayOrder=" + displayOrder +
                ", startDate=" + startDate +
                ", endDate=" + endDate +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", content=" + content +
                '}';
    }
}
