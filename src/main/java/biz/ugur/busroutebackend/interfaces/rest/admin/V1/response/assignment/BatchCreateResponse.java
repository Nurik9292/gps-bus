package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.assignment;

import biz.ugur.busroutebackend.transport.application.dto.assignment.BatchCreateResult;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record BatchCreateResponse(
        List<RouteAssignmentResponse> created,
        List<FailedAssignment> failed,

        @JsonProperty("success_count")
        int successCount,

        @JsonProperty("failed_count")
        int failedCount,

        @JsonProperty("total_requested")
        int totalRequested
) {
    public record FailedAssignment(
            @JsonProperty("vehicle_id")
            String vehicleId,
            String error
    ) {
    }

    public static BatchCreateResponse fromResult(BatchCreateResult result) {
        return new BatchCreateResponse(
                result.created().stream()
                        .map(RouteAssignmentResponse::fromData)
                        .toList(),
                result.failed().stream()
                        .map(f -> new FailedAssignment(f.vehicleId(), f.error()))
                        .toList(),
                result.successCount(),
                result.failedCount(),
                result.totalRequested()
        );
    }
}
