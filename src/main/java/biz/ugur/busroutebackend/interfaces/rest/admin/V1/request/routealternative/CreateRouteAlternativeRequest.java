package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.routealternative;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateRouteAlternativeRequest(
        @NotBlank(message = "Primary route ID is required")
        @JsonProperty("primary_route_id")
        String primaryRouteId,

        @NotBlank(message = "Alternative route ID is required")
        @JsonProperty("alternative_route_id")
        String alternativeRouteId,

        @Min(value = 1, message = "Priority must be at least 1")
        @JsonProperty("priority")
        Integer priority,

        @JsonProperty("description")
        String description,

        @JsonProperty("description_tm")
        String descriptionTm,

        @JsonProperty("description_en")
        String descriptionEn
) {}
