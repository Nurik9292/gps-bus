package biz.ugur.busroutebackend.subscription.application.dto;

import biz.ugur.busroutebackend.subscription.domain.model.SubscriptionPlanPrice;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record SubscriptionPriceResponse(
        @JsonProperty("period")       String period,
        @JsonProperty("amount_minor") long amountMinor,
        @JsonProperty("amount_major") double amountMajor,
        @JsonProperty("currency")     String currency,
        @JsonProperty("updated_by")   String updatedBy,
        @JsonProperty("updated_at")   LocalDateTime updatedAt
) {
    public static SubscriptionPriceResponse fromDomain(SubscriptionPlanPrice price) {
        return new SubscriptionPriceResponse(
                price.getPeriod().name(),
                price.getAmountMinor(),
                price.getAmountMinor() / 100.0,
                price.getCurrency(),
                price.getUpdatedBy(),
                price.getUpdatedAt()
        );
    }
}
