package biz.ugur.busroutebackend.transport.application.dto.assignment;

import java.util.List;

public record BatchCreateResult(
        List<RouteAssignmentData> created,
        List<FailedAssignment> failed,
        int successCount,
        int failedCount,
        int totalRequested
) {
    public record FailedAssignment(
            String vehicleId,
            String error
    ) {
    }
}
