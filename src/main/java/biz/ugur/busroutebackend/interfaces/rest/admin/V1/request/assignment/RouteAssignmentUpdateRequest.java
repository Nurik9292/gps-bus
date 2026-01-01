package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.assignment;

import biz.ugur.busroutebackend.transport.application.dto.assignment.UpdateRouteAssignmentCommand;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.time.LocalDate;

public record RouteAssignmentUpdateRequest(
        String vehicleId,

        String routeId,

        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate effectiveDate,

        String shiftType,

        String reason,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant expiresAt,

        Boolean isActive
) {
    public UpdateRouteAssignmentCommand toCommand(String id) {
        return new UpdateRouteAssignmentCommand(
                id,
                vehicleId,
                routeId,
                effectiveDate,
                shiftType,
                reason,
                expiresAt,
                isActive
        );
    }
}
