package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.shift;

import biz.ugur.busroutebackend.transport.application.dto.shift.CreateShiftAssignment;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ShiftAssignmentCreateRequest {

    @NotBlank(message = "Vehicle ID is required")
    @JsonProperty("vehicle_id")
    private String vehicleId;

    @NotBlank(message = "Route ID is required")
    @JsonProperty("route_id")
    private String routeId;

    @NotBlank(message = "Shift type is required")
    @Pattern(regexp = "^(FIRST|SECOND)$", message = "Shift type must be FIRST or SECOND")
    @JsonProperty("shift_type")
    private String shiftType;

    public CreateShiftAssignment toCommand() {
        return new CreateShiftAssignment(vehicleId, routeId, shiftType);
    }
}
