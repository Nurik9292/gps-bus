package biz.ugur.busroutebackend.advertising.application.dto;

import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record AdPlacementResponse(
        @JsonProperty("id")                   String id,
        @JsonProperty("business_id")          String businessId,
        @JsonProperty("tariff_id")            String tariffId,
        @JsonProperty("placement_type")       String placementType,
        @JsonProperty("status")               String status,
        @JsonProperty("title")                String title,
        @JsonProperty("content")              String content,
        @JsonProperty("image_url")            String imageUrl,
        @JsonProperty("target_url")           String targetUrl,
        @JsonProperty("cta_text")             String ctaText,

        @JsonProperty("starts_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime startsAt,

        @JsonProperty("ends_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime endsAt,

        @JsonProperty("impressions_count")    long impressionsCount,
        @JsonProperty("clicks_count")         long clicksCount,
        @JsonProperty("display_contexts")     String displayContexts,
        @JsonProperty("display_order")        Integer displayOrder,

        @JsonProperty("rejection_reason")     String rejectionReason,

        @JsonProperty("approved_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime approvedAt,

        @JsonProperty("approved_by_admin_id") String approvedByAdminId,

        @JsonProperty("rejected_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime rejectedAt,

        @JsonProperty("rejected_by_admin_id") String rejectedByAdminId,

        @JsonProperty("created_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt,

        @JsonProperty("updated_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime updatedAt
) {

    public static AdPlacementResponse fromDomain(AdPlacement p) {
        return new AdPlacementResponse(
                p.getId().getValue(),
                p.getBusinessId().getValue(),
                p.getTariffId().getValue(),
                p.getPlacementType().name(),
                p.getStatus().name(),
                p.getTitle(),
                p.getContent(),
                p.getImageUrl(),
                p.getTargetUrl(),
                p.getCtaText(),
                p.getWindow() != null ? p.getWindow().getStartsAt() : null,
                p.getWindow() != null ? p.getWindow().getEndsAt() : null,
                p.getImpressionsCount() != null ? p.getImpressionsCount() : 0L,
                p.getClicksCount() != null ? p.getClicksCount() : 0L,
                p.getDisplayContexts(),
                p.getDisplayOrder(),
                p.getRejectionReason(),
                p.getApprovedAt(),
                p.getApprovedByAdminId(),
                p.getRejectedAt(),
                p.getRejectedByAdminId(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
