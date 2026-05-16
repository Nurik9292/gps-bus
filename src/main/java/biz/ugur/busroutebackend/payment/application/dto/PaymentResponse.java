package biz.ugur.busroutebackend.payment.application.dto;

import biz.ugur.busroutebackend.payment.domain.model.Payment;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record PaymentResponse(
        @JsonProperty("id")                  String id,
        @JsonProperty("provider")            String provider,
        @JsonProperty("provider_order_id")   String providerOrderId,
        @JsonProperty("order_number")        String orderNumber,
        @JsonProperty("subject_type")        String subjectType,
        @JsonProperty("subject_id")          String subjectId,
        @JsonProperty("business_id")         String businessId,
        @JsonProperty("amount_minor")        long amountMinor,
        @JsonProperty("amount_major")        double amountMajor,
        @JsonProperty("currency")            String currency,
        @JsonProperty("status")              String status,
        @JsonProperty("form_url")            String formUrl,
        @JsonProperty("return_url")          String returnUrl,

        @JsonProperty("initiated_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime initiatedAt,

        @JsonProperty("completed_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime completedAt,

        @JsonProperty("completed_by")        String completedBy,

        @JsonProperty("failed_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime failedAt,

        @JsonProperty("failure_code")        String failureCode,
        @JsonProperty("failure_message")     String failureMessage,
        @JsonProperty("card_pan_masked")     String cardPanMasked
) {

    public static PaymentResponse fromDomain(Payment p) {
        return new PaymentResponse(
                p.getId().getValue(),
                p.getProvider().name(),
                p.getProviderOrderId(),
                p.getOrderNumber().getValue(),
                p.getSubjectType().name(),
                p.getSubjectId(),
                p.getBusinessId(),
                p.getMoney().getAmountMinor(),
                p.getMoney().toMajorUnits(),
                p.getMoney().getCurrency(),
                p.getStatus().name(),
                p.getFormUrl(),
                p.getReturnUrl(),
                p.getInitiatedAt(),
                p.getCompletedAt(),
                p.getCompletedBy(),
                p.getFailedAt(),
                p.getFailureCode(),
                p.getFailureMessage(),
                p.getCardPanMasked()
        );
    }
}
