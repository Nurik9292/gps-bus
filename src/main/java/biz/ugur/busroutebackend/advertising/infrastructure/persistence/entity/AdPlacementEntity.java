package biz.ugur.busroutebackend.advertising.infrastructure.persistence.entity;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Builder
@Getter
@Table("ad_placements")
@EqualsAndHashCode(callSuper = false)
public class AdPlacementEntity {

    @Id @Column("id")                    private String id;
    @Column("business_id")               private String businessId;
    @Column("tariff_id")                 private String tariffId;
    @Column("placement_type")            private String placementType;
    @Column("status")                    private String status;
    @Column("title")                     private String title;
    @Column("content")                   private String content;
    @Column("image_url")                 private String imageUrl;
    @Column("target_url")                private String targetUrl;
    @Column("cta_text")                  private String ctaText;
    @Column("starts_at")                 private LocalDateTime startsAt;
    @Column("ends_at")                   private LocalDateTime endsAt;
    @Column("impressions_count")         private Long impressionsCount;
    @Column("clicks_count")              private Long clicksCount;
    @Column("display_contexts")          private String displayContexts;
    @Column("display_order")             private Integer displayOrder;
    @Column("rejection_reason")          private String rejectionReason;
    @Column("approved_at")               private LocalDateTime approvedAt;
    @Column("approved_by_admin_id")      private String approvedByAdminId;
    @Column("rejected_at")               private LocalDateTime rejectedAt;
    @Column("rejected_by_admin_id")      private String rejectedByAdminId;
    @CreatedDate @Column("created_at")   private LocalDateTime createdAt;
    @LastModifiedDate @Column("updated_at") private LocalDateTime updatedAt;
    @Column("version")                   private Long version;
}
