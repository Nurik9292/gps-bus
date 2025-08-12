package biz.ugur.busroutebackend.admin.domain.model;

import biz.ugur.busroutebackend.admin.domain.events.BannerCreatedEvent;
import biz.ugur.busroutebackend.admin.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.shared.domain.AggregateRoot;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
@Table("banners")
public class Banner extends AggregateRoot<Banner, BannerId> {

    @Id
    @Column("id")
    private BannerId id;

    @Column("title")
    private String title;

    @Column("type")
    private String type;

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

    public Banner() {}

    public Banner(String title, String type, String imageUrl, String targetUrl, Integer displayOrder) {
        this.id = BannerId.generate();
        this.title = validateTitle(title);
        this.type = type;
        this.imageUrl = validateImageUrl(imageUrl);
        this.targetUrl = targetUrl;
        this.isActive = true;
        this.displayOrder = displayOrder != null ? displayOrder : 0;
        this.startDate = LocalDateTime.now();
        this.endDate = null;

        registerEvent(new BannerCreatedEvent(
                this.id.getValue(),
                this.title,
                this.type,
                this.imageUrl
        ));
    }

    public static Banner restore(BannerId id, String title, String type, String imageUrl, String targetUrl,
                                 Boolean isActive, Integer displayOrder, LocalDateTime startDate, LocalDateTime endDate) {
        Banner banner = new Banner();
        banner.id = id;
        banner.title = title;
        banner.type = type;
        banner.imageUrl = imageUrl;
        banner.targetUrl = targetUrl;
        banner.isActive = isActive;
        banner.displayOrder = displayOrder;
        banner.startDate = startDate;
        banner.endDate = endDate;
        return banner;
    }

    public void updateBanner(String title, String type, String imageUrl, String targetUrl, Integer displayOrder) {
        if (title != null && !title.trim().isEmpty()) {
            this.title = title.trim();
        }

        if (type != null && !type.trim().isEmpty()) {
            this.type = type.trim();
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
                Objects.equals(endDate, banner.endDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), id, title, type, imageUrl, targetUrl, isActive, displayOrder, startDate, endDate);
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
                '}';
    }
}
