package biz.ugur.busroutebackend.interfaces.rest.client.V1.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Phone is required")
        String phone,

        @NotBlank(message = "OTP is required")
        String otp
) {}
