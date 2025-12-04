package biz.ugur.busroutebackend.transport.application.dto.shift;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateShiftAssignment(
        @NotBlank(message = "Vehicle ID is required")
        String vehicleId,

        @NotBlank(message = "Route ID is required")
        String routeId,

        @NotBlank(message = "Shift type is required")
        @Pattern(regexp = "^(FIRST|SECOND)$", message = "Shift type must be FIRST or SECOND")
        String shiftType
) {
}
