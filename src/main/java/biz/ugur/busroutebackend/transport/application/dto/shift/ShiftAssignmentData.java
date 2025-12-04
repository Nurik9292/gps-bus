package biz.ugur.busroutebackend.transport.application.dto.shift;

import java.time.LocalDateTime;
import java.time.LocalTime;

public record ShiftAssignmentData(
        String id,
        String vehicleId,
        String vehicleLicensePlate,
        String vehicleDeviceId,
        String routeId,
        String routeNumber,
        String routeName,
        String shiftType,
        LocalTime shiftStartTime,
        LocalTime shiftEndTime,
        Boolean isActive,
        Boolean isCurrentlyActive,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
