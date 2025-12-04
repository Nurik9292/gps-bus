package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.shift;

import biz.ugur.busroutebackend.transport.application.dto.shift.UpdateShiftAssignment;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ShiftAssignmentUpdateRequest {

    @NotBlank(message = "Route ID is required")
    @JsonProperty("route_id")
    private String routeId;

    public UpdateShiftAssignment toCommand() {
        return new UpdateShiftAssignment(routeId);
    }
}
