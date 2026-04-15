package biz.ugur.busroutebackend.payment.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InitiatePaymentCommand(
        @JsonProperty("provider")       String provider,        // RYSGAL / SENAGAT / ...
        @JsonProperty("subject_type")   String subjectType,     // AD_PLACEMENT
        @JsonProperty("subject_id")     String subjectId,
        @JsonProperty("business_id")    String businessId,      // optional, for reports
        @JsonProperty("amount_minor")   Long amountMinor,
        @JsonProperty("currency")       String currency,        // default TMT
        @JsonProperty("return_url")     String returnUrl,
        @JsonProperty("expires_in_min") Integer expiresInMinutes // default 30
) {}
