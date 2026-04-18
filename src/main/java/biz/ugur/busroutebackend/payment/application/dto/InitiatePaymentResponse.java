package biz.ugur.busroutebackend.payment.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record InitiatePaymentResponse(
        @JsonProperty("payment_id")   String paymentId,
        @JsonProperty("order_number") String orderNumber,
        @JsonProperty("form_url")     String formUrl,
        @JsonProperty("provider")     String provider
) {}
