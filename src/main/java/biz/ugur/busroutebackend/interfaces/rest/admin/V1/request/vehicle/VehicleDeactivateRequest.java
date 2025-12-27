package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.vehicle;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

public record VehicleDeactivateRequest(
        @Size(max = 500, message = "Reason must not exceed 500 characters")
        @JsonProperty("reason")
        String reason
) {
}
