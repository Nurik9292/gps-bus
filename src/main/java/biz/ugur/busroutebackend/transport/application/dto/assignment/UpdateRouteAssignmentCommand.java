package biz.ugur.busroutebackend.transport.application.dto.assignment;

import java.time.Instant;
import java.time.LocalDate;

public record UpdateRouteAssignmentCommand(
        String id,
        String vehicleId,
        String routeId,
        LocalDate effectiveDate,
        String shiftType,
        String reason,
        Instant expiresAt,
        Boolean isActive
) {
}
