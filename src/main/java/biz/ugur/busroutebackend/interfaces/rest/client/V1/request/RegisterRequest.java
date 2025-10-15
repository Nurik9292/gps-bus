package biz.ugur.busroutebackend.interfaces.rest.client.V1.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegisterRequest(
        @NotBlank(message = "Name is required")
        String name,

        @NotBlank(message = "Phone is required")
        @Pattern(
                regexp = "^(?:\\+993|993)[1-9][0-9]{7}$",
                message = "Invalid Turkmenistan phone number"
        )
        String phone,

        @NotBlank(message = "Platform is required")
        String platform
) {}