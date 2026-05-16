package biz.ugur.busroutebackend.advertising.application.dto;

import biz.ugur.busroutebackend.payment.domain.model.Payment;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PaymentInfo(
        @JsonProperty("id")       String id,
        @JsonProperty("provider") String provider,
        @JsonProperty("status")   String status,
        @JsonProperty("amount")   long amount,
        @JsonProperty("currency") String currency,
        @JsonProperty("form_url") String formUrl
) {
    public static PaymentInfo fromDomain(Payment p) {
        return new PaymentInfo(
                p.getId().getValue(),
                p.getProvider().name(),
                p.getStatus().name(),
                p.getMoney().getAmountMinor(),
                p.getMoney().getCurrency(),
                p.getFormUrl()
        );
    }
}
