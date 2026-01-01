package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.assignment;

import biz.ugur.busroutebackend.transport.application.dto.assignment.RouteAssignmentData;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;


public record RouteAssignmentResponse(
        String id,
        String vehicleId,
        String vehicleLicensePlate,
        String vehicleDeviceId,
        String routeId,
        String routeNumber,
        String routeName,

        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate effectiveDate,

        String shiftType,

        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime,

        @JsonFormat(pattern = "HH:mm")
        LocalTime endTime,

        String assignedBy,
        String reason,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
        Instant expiresAt,

        Boolean isActive,
        Boolean isForCurrentShift,
        Boolean isExpired,

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "Asia/Ashgabat")
        LocalDateTime createdAt,

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
                data.startTime(),
                data.endTime(),
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
