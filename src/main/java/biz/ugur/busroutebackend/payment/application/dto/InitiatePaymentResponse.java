package biz.ugur.busroutebackend.payment.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** What we hand back to the admin UI: where to redirect the customer + our tracking ids. */
public record InitiatePaymentResponse(
        @JsonProperty("payment_id")   String paymentId,
        @JsonProperty("order_number") String orderNumber,
        @JsonProperty("form_url")     String formUrl,
        @JsonProperty("provider")     String provider
) {}
