package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.routealternative;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;

public record UpdateRouteAlternativeRequest(
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
