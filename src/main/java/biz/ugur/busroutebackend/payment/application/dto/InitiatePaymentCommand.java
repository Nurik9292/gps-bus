package biz.ugur.busroutebackend.payment.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InitiatePaymentCommand(
        @JsonProperty("provider")       String provider,       
        @JsonProperty("subject_type")   String subjectType,     
        @JsonProperty("subject_id")     String subjectId,
        @JsonProperty("business_id")    String businessId,     
        @JsonProperty("amount_minor")   Long amountMinor,
        @JsonProperty("currency")       String currency,       
        @JsonProperty("return_url")     String returnUrl,
        @JsonProperty("expires_in_min") Integer expiresInMinutes
) {}
