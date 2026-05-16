package biz.ugur.busroutebackend.advertising.application.dto;

import biz.ugur.busroutebackend.advertising.domain.enums.ContentType;
import biz.ugur.busroutebackend.advertising.domain.enums.PlacementKind;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public record CreateAdPlacementCommand(
        @JsonProperty("business_id")       String businessId,
        @JsonProperty("tariff_id")         String tariffId,
        @JsonProperty("placement_type")    String placementType,
        @JsonProperty("kind")              String kind,
        @JsonProperty("title")             String title,
        @JsonProperty("content")           String content,
        @JsonProperty("image_url")         String imageUrl,
        @JsonProperty("target_url")        String targetUrl,
        @JsonProperty("cta_text")          String ctaText,
        @JsonProperty("starts_at")         LocalDateTime startsAt,
        @JsonProperty("ends_at")           LocalDateTime endsAt,
        @JsonProperty("targets")           List<PlacementTargetSpec> targets,
        @JsonProperty("display_order")     Integer displayOrder,
        @JsonProperty("payment_method")    PaymentMethod paymentMethod,
        @JsonProperty("payment_provider")  String paymentProvider,
        @JsonProperty("content_type")      ContentType contentType
) {
    public void validatePaymentConsistency() {
        PlacementKind resolvedKind = PlacementKind.from(kind);
        boolean commercial = resolvedKind == PlacementKind.COMMERCIAL;
        if (!commercial && (paymentMethod != null || businessId != null || tariffId != null)) {
            throw new IllegalArgumentException("EDITORIAL must not have paymentMethod/businessId/tariffId");
        }
        if (commercial && (businessId == null || tariffId == null)) {
            throw new IllegalArgumentException("COMMERCIAL requires businessId and tariffId");
        }
        if (commercial && paymentMethod == null) {
            throw new IllegalArgumentException("COMMERCIAL requires paymentMethod");
        }
        if (paymentMethod == PaymentMethod.BANK && (paymentProvider == null || paymentProvider.isBlank())) {
            throw new IllegalArgumentException("paymentProvider required when paymentMethod=BANK");
        }
        if (paymentMethod == PaymentMethod.CASH && paymentProvider != null) {
            throw new IllegalArgumentException("paymentProvider must be null when paymentMethod=CASH");
        }
    }

    public void validateContentConsistency() {
        if (contentType == null) {
            throw new IllegalArgumentException("contentType required");
        }
        if (contentType == ContentType.CONTENT && (content == null || content.isBlank())) {
            throw new IllegalArgumentException("content required for contentType=CONTENT");
        }
        if (contentType == ContentType.LINK && (targetUrl == null || targetUrl.isBlank())) {
            throw new IllegalArgumentException("targetUrl required for contentType=LINK");
        }
        if (contentType == ContentType.LINK && content != null && !content.isBlank()) {
            throw new IllegalArgumentException("content not allowed for contentType=LINK");
        }
    }
}
