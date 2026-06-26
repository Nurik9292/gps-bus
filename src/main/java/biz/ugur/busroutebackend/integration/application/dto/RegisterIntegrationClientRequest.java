package biz.ugur.busroutebackend.integration.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record RegisterIntegrationClientRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
        String name,

        @NotBlank(message = "External user ID is required")
        @Size(max = 255, message = "External user ID must not exceed 255 characters")
        String externalUserId,

        @Size(max = 30, message = "Phone must not exceed 30 characters")
        String phone
) {
}
