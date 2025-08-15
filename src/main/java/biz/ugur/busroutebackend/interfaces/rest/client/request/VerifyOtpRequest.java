package biz.ugur.busroutebackend.interfaces.rest.client.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VerifyOtpRequest(
        @NotBlank(message = "Phone is required")
        String phone,

        @NotBlank(message = "OTP is required")
        @Pattern(regexp = "\\d{5}", message = "OTP must be 5 digits")
        String otp
) {}