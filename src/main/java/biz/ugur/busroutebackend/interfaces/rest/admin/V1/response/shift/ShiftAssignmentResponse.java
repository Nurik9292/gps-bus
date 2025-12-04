package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.shift;

import biz.ugur.busroutebackend.transport.application.dto.shift.ShiftAssignmentData;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.time.LocalTime;

public record ShiftAssignmentResponse(
        String id,
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("vehicle_license_plate") String vehicleLicensePlate,
        @JsonProperty("vehicle_device_id") String vehicleDeviceId,
        @JsonProperty("route_id") String routeId,
        @JsonProperty("route_number") String routeNumber,
        @JsonProperty("route_name") String routeName,
        @JsonProperty("shift_type") String shiftType,
        @JsonProperty("shift_start_time") LocalTime shiftStartTime,
        @JsonProperty("shift_end_time") LocalTime shiftEndTime,
        @JsonProperty("is_active") Boolean isActive,
        @JsonProperty("is_currently_active") Boolean isCurrentlyActive,
        @JsonProperty("created_at") LocalDateTime createdAt,
        @JsonProperty("updated_at") LocalDateTime updatedAt
) {
    public static ShiftAssignmentResponse fromData(ShiftAssignmentData data) {
        return new ShiftAssignmentResponse(
                data.id(),
                data.vehicleId(),
                data.vehicleLicensePlate(),
                data.vehicleDeviceId(),
                data.routeId(),
                data.routeNumber(),
                data.routeName(),
                data.shiftType(),
                data.shiftStartTime(),
                data.shiftEndTime(),
                data.isActive(),
                data.isCurrentlyActive(),
                data.createdAt(),
                data.updatedAt()
        );
    }
}
