package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.immediate;

import biz.ugur.busroutebackend.transport.application.dto.immediate.ImmediateAssignmentData;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ImmediateAssignmentResponse(
        String id,
        @JsonProperty("vehicle_id")
        String vehicleId,
        @JsonProperty("vehicle_license_plate")
        String vehicleLicensePlate,
        @JsonProperty("route_id")
        String routeId,
        @JsonProperty("route_number")
        String routeNumber,
        @JsonProperty("route_name")
        String routeName,
        @JsonProperty("assigned_by")
        String assignedBy,
        String reason,
        @JsonProperty("assigned_at")
        Instant assignedAt,
        @JsonProperty("expires_at")
        Instant expiresAt,
        @JsonProperty("is_active")
        Boolean isActive,
        @JsonProperty("is_expired")
        boolean isExpired,
        @JsonProperty("is_currently_valid")
        boolean isCurrentlyValid
) {
    public static ImmediateAssignmentResponse fromData(ImmediateAssignmentData data) {
        return new ImmediateAssignmentResponse(
                data.id(),
                data.vehicleId(),
                data.vehicleLicensePlate(),
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
