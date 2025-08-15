package biz.ugur.busroutebackend.interfaces.rest.client.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Phone is required")
        String phone,

        @NotBlank(message = "OTP is required")
        String otp
) {}
