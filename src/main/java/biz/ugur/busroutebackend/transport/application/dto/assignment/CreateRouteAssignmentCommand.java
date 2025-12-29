package biz.ugur.busroutebackend.transport.application.dto.assignment;

import java.time.Instant;
import java.time.LocalDate;

public record CreateRouteAssignmentCommand(
        String vehicleId,
        String routeId,
        LocalDate effectiveDate,
        String shiftType,
        String assignedBy,
        String reason,
        Instant expiresAt
) {
}
