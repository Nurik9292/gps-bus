package biz.ugur.busroutebackend.interfaces.rest.client.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CenterRequest(
        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^(\\+993|993)?[1-9]\\d{7}$", message = "Invalid Turkmenistan phone number")
        String phone,

        @NotBlank(message = "Platform is required")
        String platform
) {
}
