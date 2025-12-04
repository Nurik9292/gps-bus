package biz.ugur.busroutebackend.transport.application.dto.shift;

import jakarta.validation.constraints.NotBlank;

public record UpdateShiftAssignment(
        @NotBlank(message = "Route ID is required")
        String routeId
) {
}
