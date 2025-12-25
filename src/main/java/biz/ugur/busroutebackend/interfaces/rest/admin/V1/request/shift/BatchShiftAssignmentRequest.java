package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.shift;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record BatchShiftAssignmentRequest(
        @NotEmpty(message = "Vehicle IDs list cannot be empty")
        @Size(max = 100, message = "Maximum 100 vehicles per batch")
        @JsonProperty("vehicle_ids")
        List<String> vehicleIds,

        @NotBlank(message = "Route ID is required")
        @JsonProperty("route_id")
        String routeId,

        @NotBlank(message = "Shift type is required")
        @JsonProperty("shift_type")
        String shiftType
) {}
