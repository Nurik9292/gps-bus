package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.assignment;

import biz.ugur.busroutebackend.transport.application.dto.assignment.RouteAssignmentData;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;


public record RouteAssignmentResponse(
        String id,
        String vehicleId,
        String routeId,

        @JsonFormat(pattern = "yyyy-MM-dd")
        LocalDate effectiveDate,

        String shiftType,
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
                data.routeId(),
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
