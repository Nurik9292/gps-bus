package biz.ugur.busroutebackend.subscription.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record InitiateSubscriptionPaymentResponse(
        @JsonProperty("subscription_id") String subscriptionId,
        @JsonProperty("payment_id")      String paymentId,
        @JsonProperty("order_number")    String orderNumber,
        @JsonProperty("form_url")        String formUrl,
        @JsonProperty("return_url")      String returnUrl,
        @JsonProperty("provider")        String provider,
        @JsonProperty("period")          String period,
        @JsonProperty("amount_minor")    long amountMinor,
        @JsonProperty("currency")        String currency,
        @JsonProperty("expires_at")      LocalDateTime expiresAt
) {}
