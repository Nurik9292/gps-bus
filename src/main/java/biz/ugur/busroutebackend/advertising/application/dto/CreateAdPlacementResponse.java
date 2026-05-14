package biz.ugur.busroutebackend.advertising.application.dto;

import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.payment.domain.model.Payment;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateAdPlacementResponse(
        @JsonProperty("placement") AdPlacementResponse placement,
        @JsonProperty("payment")   PaymentInfo payment
) {
    public static CreateAdPlacementResponse of(AdPlacement placement, Payment payment) {
        return new CreateAdPlacementResponse(
                AdPlacementResponse.fromDomain(placement),
                payment != null ? PaymentInfo.fromDomain(payment) : null
        );
    }
}
