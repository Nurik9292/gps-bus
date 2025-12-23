package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.immediate;

import biz.ugur.busroutebackend.transport.application.dto.immediate.ImmediateAssignmentData;

import java.time.Instant;

public record ImmediateAssignmentResponse(
        String id,
        String vehicleId,
        String routeId,
        String routeNumber,
        String routeName,
        String assignedBy,
        String reason,
        Instant assignedAt,
        Instant expiresAt,
        Boolean isActive,
        boolean isExpired,
        boolean isCurrentlyValid
) {
    public static ImmediateAssignmentResponse fromData(ImmediateAssignmentData data) {
        return new ImmediateAssignmentResponse(
                data.id(),
                data.vehicleId(),
                data.routeId(),
                data.routeNumber(),
                data.routeName(),
                data.assignedBy(),
                data.reason(),
                data.assignedAt(),
                data.expiresAt(),
                data.isActive(),
                data.isExpired(),
                data.isCurrentlyValid()
        );
    }
}
