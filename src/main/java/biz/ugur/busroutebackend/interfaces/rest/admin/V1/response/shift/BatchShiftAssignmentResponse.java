package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.shift;

import biz.ugur.busroutebackend.transport.application.usecase.shift.BatchCreateShiftAssignmentUseCase;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record BatchShiftAssignmentResponse(
        @JsonProperty("created")
        List<ShiftAssignmentResponse> created,

        @JsonProperty("failed")
        List<FailedAssignment> failed,

        @JsonProperty("success_count")
        int successCount,

        @JsonProperty("failed_count")
        int failedCount,

        @JsonProperty("total_requested")
        int totalRequested
) {
    public static BatchShiftAssignmentResponse fromResult(BatchCreateShiftAssignmentUseCase.Result result) {
        List<ShiftAssignmentResponse> created = result.created().stream()
                .map(ShiftAssignmentResponse::fromData)
                .toList();

        List<FailedAssignment> failed = result.failed().stream()
                .map(f -> new FailedAssignment(f.vehicleId(), f.error()))
                .toList();

        return new BatchShiftAssignmentResponse(
                created,
                failed,
                result.successCount(),
                result.failedCount(),
                result.successCount() + result.failedCount()
        );
    }

    public record FailedAssignment(
            @JsonProperty("vehicle_id")
            String vehicleId,

            String error
    ) {}
}
