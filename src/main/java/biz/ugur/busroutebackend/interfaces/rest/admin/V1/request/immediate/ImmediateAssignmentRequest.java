package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.immediate;

import biz.ugur.busroutebackend.transport.application.usecase.immediate.CreateImmediateAssignmentUseCase;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record ImmediateAssignmentRequest(
        @NotBlank(message = "Route ID is required")
        @JsonProperty("route_id")
        String routeId,

        @Size(max = 500, message = "Reason cannot exceed 500 characters")
        String reason,

        @JsonProperty("expires_at")
        Instant expiresAt
) {
    public CreateImmediateAssignmentUseCase.Command toCommand(String vehicleId, String assignedBy) {
        return new CreateImmediateAssignmentUseCase.Command(
                vehicleId,
                routeId,
                assignedBy,
                reason,
                expiresAt
        );
    }
}
