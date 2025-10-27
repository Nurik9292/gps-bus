package biz.ugur.busroutebackend.banner.infrastructure.persistence.entity;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDateTime;

@Builder
@Table("banners")
@Getter
@EqualsAndHashCode(callSuper = false)
public class BannerEntity {
    @Id
    @Column("id")
    private String id;

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

    @Column("content")
    private String content;

    @CreatedDate
    @Column("created_at")
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column("updated_at")
    private LocalDateTime updatedAt;

    private Long version;
}
