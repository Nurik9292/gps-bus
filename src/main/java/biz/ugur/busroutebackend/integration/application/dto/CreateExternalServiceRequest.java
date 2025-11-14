package biz.ugur.busroutebackend.integration.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;


public record CreateExternalServiceRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 3, max = 255, message = "Name must be between 3 and 255 characters")
        String name,

        String description,

        List<String> allowedEndpoints,

        @Min(value = 1, message = "Rate limit must be positive")
        Integer rateLimitPerMinute
) {
}
