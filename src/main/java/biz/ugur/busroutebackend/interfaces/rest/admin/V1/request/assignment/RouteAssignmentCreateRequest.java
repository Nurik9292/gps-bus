package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.assignment;

import biz.ugur.busroutebackend.transport.application.dto.assignment.CreateRouteAssignmentCommand;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.time.LocalDate;

public record RouteAssignmentCreateRequest(
        @NotBlank(message = "Vehicle ID is required")
        String vehicleId,

        @NotBlank(message = "Route ID is required")
        String routeId,

        @NotNull(message = "Effective date is required")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate effectiveDate,

        @NotBlank(message = "Shift type is required")
        String shiftType,

        @NotBlank(message = "Assigned by is required")
        String assignedBy,

        String reason,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant expiresAt
) {
    public CreateRouteAssignmentCommand toCommand() {
        return new CreateRouteAssignmentCommand(
                vehicleId,
                routeId,
                effectiveDate,
                shiftType,
                assignedBy,
                reason,
                expiresAt
        );
    }
}
