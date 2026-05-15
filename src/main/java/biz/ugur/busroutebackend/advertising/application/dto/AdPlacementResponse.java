package biz.ugur.busroutebackend.advertising.application.dto;

import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public record AdPlacementResponse(
        @JsonProperty("id")                   String id,
        @JsonProperty("business_id")          String businessId,
        @JsonProperty("tariff_id")            String tariffId,
        @JsonProperty("placement_type")       String placementType,
        @JsonProperty("kind")                 String kind,
        @JsonProperty("status")               String status,
        @JsonProperty("title")                String title,
        @JsonProperty("content")              String content,
        @JsonProperty("content_type")         String contentType,
        @JsonProperty("image_url")            String imageUrl,
        @JsonProperty("target_url")           String targetUrl,
        @JsonProperty("cta_text")             String ctaText,

        @JsonProperty("starts_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime startsAt,

        @JsonProperty("ends_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime endsAt,

        @JsonProperty("targets")              List<PlacementTargetView> targets,
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
        LocalDateTime updatedAt,

        @JsonProperty("pending_cash_payment") PaymentInfo pendingCashPayment
) {

    public static AdPlacementResponse fromDomain(AdPlacement p) {
        List<PlacementTargetView> targetViews = p.getTargets() == null
                ? List.of()
                : p.getTargets().stream().map(PlacementTargetView::fromDomain).toList();
        return new AdPlacementResponse(
                p.getId().getValue(),
                p.getBusinessId() != null ? p.getBusinessId().getValue() : null,
                p.getTariffId() != null ? p.getTariffId().getValue() : null,
                p.getPlacementType().name(),
                p.getKind() != null ? p.getKind().name() : null,
                p.getStatus().name(),
                p.getTitle(),
                p.getContent(),
                p.getContentType() != null ? p.getContentType().name() : null,
                p.getImageUrl(),
                p.getTargetUrl(),
                p.getCtaText(),
                p.getWindow() != null ? p.getWindow().getStartsAt() : null,
                p.getWindow() != null ? p.getWindow().getEndsAt() : null,
                targetViews,
                p.getDisplayOrder(),
                p.getRejectionReason(),
                p.getApprovedAt(),
                p.getApprovedByAdminId(),
                p.getRejectedAt(),
                p.getRejectedByAdminId(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                null
        );
    }
}
