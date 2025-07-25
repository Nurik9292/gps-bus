package biz.ugur.busroutebackend.admin.domain.model;

import biz.ugur.busroutebackend.admin.domain.events.BannerCreatedEvent;
import biz.ugur.busroutebackend.admin.domain.valueobjects.BannerId;
import biz.ugur.busroutebackend.shared.domain.AggregateRoot;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Table("banners")
public class Banner extends AggregateRoot<Banner, BannerId> {

    @Id
    @Column("id")
    private BannerId id;

    @Column("title")
    private String title;

    @Column("image_url")
    private String imageUrl;

    @Column("target_url")
    private String targetUrl;

    @Column("is_active")
    private Boolean isActive;

    @Column("display_order")
    private Integer displayOrder;

    @Column("start_date")
    private java.time.LocalDateTime startDate;

    @Setter
    @Column("end_date")
    private java.time.LocalDateTime endDate;

    public Banner(String title, String imageUrl, String targetUrl, Integer displayOrder) {
        this.id = BannerId.generate();
        this.title = validateTitle(title);
        this.imageUrl = validateImageUrl(imageUrl);
        this.targetUrl = targetUrl;
        this.isActive = true;
        this.displayOrder = displayOrder != null ? displayOrder : 0;
        this.startDate = java.time.LocalDateTime.now();

        registerEvent(new BannerCreatedEvent(
                this.id.getValue(),
                this.title,
                this.imageUrl
        ));
    }

    public Banner(BannerId id, String title, String imageUrl, String targetUrl,
                  Boolean isActive, Integer displayOrder,
                  java.time.LocalDateTime startDate, java.time.LocalDateTime endDate) {
        this.id = id;
        this.title = title;
        this.imageUrl = imageUrl;
        this.targetUrl = targetUrl;
        this.isActive = isActive;
        this.displayOrder = displayOrder;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public void updateBanner(String title, String targetUrl, Integer displayOrder) {
        if (title != null && !title.trim().isEmpty()) {
            this.title = title.trim();
        }
        if (targetUrl != null) {
            this.targetUrl = targetUrl.trim();
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
}
