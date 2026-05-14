package biz.ugur.busroutebackend.advertising.application.dto;

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
        @JsonProperty("payment_provider")  String paymentProvider
) {
    public void validatePaymentConsistency() {
        PlacementKind resolvedKind = PlacementKind.from(kind);
        boolean commercial = resolvedKind == PlacementKind.COMMERCIAL;
        if (commercial && paymentMethod == null) {
            throw new IllegalArgumentException("paymentMethod required for COMMERCIAL placements");
        }
        if (!commercial && paymentMethod != null) {
            throw new IllegalArgumentException("paymentMethod not allowed for non-COMMERCIAL placements");
        }
        if (paymentMethod == PaymentMethod.BANK && (paymentProvider == null || paymentProvider.isBlank())) {
            throw new IllegalArgumentException("paymentProvider required when paymentMethod=BANK");
        }
        if (paymentMethod == PaymentMethod.CASH && paymentProvider != null) {
            throw new IllegalArgumentException("paymentProvider must be null when paymentMethod=CASH");
        }
    }
}
