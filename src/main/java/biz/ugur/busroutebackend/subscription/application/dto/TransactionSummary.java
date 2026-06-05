package biz.ugur.busroutebackend.subscription.application.dto;

import biz.ugur.busroutebackend.payment.application.dto.PaymentResponse;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record TransactionSummary(
        @JsonProperty("payment_id")       String paymentId,
        @JsonProperty("status")           String status,
        @JsonProperty("provider")         String provider,
        @JsonProperty("amount_minor")     long amountMinor,
        @JsonProperty("currency")         String currency,
        @JsonProperty("completed_at")     LocalDateTime completedAt,
        @JsonProperty("failure_message")  String failureMessage
) {
    public static TransactionSummary fromPaymentResponse(PaymentResponse p) {
        return new TransactionSummary(
                p.id(),
                p.status(),
                p.provider(),
                p.amountMinor(),
                p.currency(),
                p.completedAt(),
                p.failureMessage()
        );
    }
}
