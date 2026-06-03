package biz.ugur.busroutebackend.subscription.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record InitiateSubscriptionPaymentCommand(
        @JsonProperty("client_id") @NotBlank String clientId,
        @JsonProperty("bank")      @NotBlank String bank,
        @JsonProperty("period")    @NotBlank String period
) {}
