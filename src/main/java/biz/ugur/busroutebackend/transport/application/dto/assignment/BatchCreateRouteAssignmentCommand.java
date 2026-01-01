package biz.ugur.busroutebackend.transport.application.dto.assignment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record BatchCreateRouteAssignmentCommand(
        List<String> vehicleIds,
        String routeId,
        LocalDate effectiveDate,
        String shiftType,
        String assignedBy,
        String reason,
        Instant expiresAt
) {
}
