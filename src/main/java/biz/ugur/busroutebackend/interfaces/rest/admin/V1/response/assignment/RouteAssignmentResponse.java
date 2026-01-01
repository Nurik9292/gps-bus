package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.assignment;

import biz.ugur.busroutebackend.transport.application.dto.assignment.RouteAssignmentData;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;


public record RouteAssignmentResponse(
        String id,

        @JsonProperty("vehicle_id")
        String vehicleId,

        @JsonProperty("vehicle_license_plate")
        String vehicleLicensePlate,

        @JsonProperty("vehicle_device_id")
        String vehicleDeviceId,

        @JsonProperty("route_id")
        String routeId,

        @JsonProperty("route_number")
        String routeNumber,

        @JsonProperty("route_name")
        String routeName,

        @JsonProperty("effective_date")
        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate effectiveDate,

        @JsonProperty("shift_type")
        String shiftType,

        @JsonProperty("assigned_by")
        String assignedBy,

        String reason,

        @JsonProperty("expires_at")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant expiresAt,

        @JsonProperty("is_active")
        Boolean isActive,

        @JsonProperty("is_for_current_shift")
        Boolean isForCurrentShift,

        @JsonProperty("is_expired")
        Boolean isExpired,

        @JsonProperty("created_at")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Ashgabat")
        LocalDateTime createdAt,

        @JsonProperty("updated_at")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Ashgabat")
        LocalDateTime updatedAt,

        Long version
) {

    public static RouteAssignmentResponse fromData(RouteAssignmentData data) {
        return new RouteAssignmentResponse(
                data.id(),
                data.vehicleId(),
                data.vehicleLicensePlate(),
                data.vehicleDeviceId(),
                data.routeId(),
                data.routeNumber(),
                data.routeName(),
                data.effectiveDate(),
                data.shiftType(),
                data.assignedBy(),
                data.reason(),
                data.expiresAt(),
                data.isActive(),
                data.isForCurrentShift(),
                data.isExpired(),
                data.createdAt(),
                data.updatedAt(),
                data.version()
        );
    }
}
