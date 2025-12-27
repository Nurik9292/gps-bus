package biz.ugur.busroutebackend.transport.application.dto.immediate;

import java.time.Instant;

public record ImmediateAssignmentData(
        String id,
        String vehicleId,
        String vehicleLicensePlate,
        String routeId,
        String routeNumber,
        String routeName,
        String assignedBy,
        String reason,
        Instant assignedAt,
        Instant expiresAt,
        Boolean isActive
) {
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    public boolean isCurrentlyValid() {
        return Boolean.TRUE.equals(isActive) && !isExpired();
    }
}
