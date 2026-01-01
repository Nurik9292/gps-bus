package biz.ugur.busroutebackend.transport.application.dto.assignment;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;


public record RouteAssignmentData(
        String id,
        String vehicleId,
        String vehicleLicensePlate,
        String vehicleDeviceId,
        String routeId,
        String routeNumber,
        String routeName,
        LocalDate effectiveDate,
        String shiftType,
        String assignedBy,
        String reason,
        Instant expiresAt,
        Boolean isActive,
        Boolean isForCurrentShift,
        Boolean isExpired,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version
) {
}
