package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.immediate;

import biz.ugur.busroutebackend.transport.application.usecase.immediate.GetAllImmediateAssignmentsUseCase;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ImmediateAssignmentListResponse(
        List<ImmediateAssignmentItem> assignments,
        @JsonProperty("total_count")
        int totalCount
) {
    public static ImmediateAssignmentListResponse fromResult(GetAllImmediateAssignmentsUseCase.Result result) {
        List<ImmediateAssignmentItem> items = result.assignments().stream()
                .map(ImmediateAssignmentItem::fromData)
                .toList();
        return new ImmediateAssignmentListResponse(items, result.totalCount());
    }

    public record ImmediateAssignmentItem(
            String id,
            @JsonProperty("vehicle_id")
            String vehicleId,
            @JsonProperty("vehicle_license_plate")
            String vehicleLicensePlate,
            @JsonProperty("route_id")
            String routeId,
            @JsonProperty("route_number")
            String routeNumber,
            @JsonProperty("route_name")
            String routeName,
            @JsonProperty("assigned_by")
            String assignedBy,
            String reason,
            @JsonProperty("assigned_at")
            String assignedAt,
            @JsonProperty("expires_at")
            String expiresAt,
            @JsonProperty("is_active")
            Boolean isActive
    ) {
        public static ImmediateAssignmentItem fromData(GetAllImmediateAssignmentsUseCase.ImmediateAssignmentWithVehicle data) {
            return new ImmediateAssignmentItem(
                    data.id(),
                    data.vehicleId(),
                    data.vehicleLicensePlate(),
                    data.routeId(),
                    data.routeNumber(),
                    data.routeName(),
                    data.assignedBy(),
                    data.reason(),
                    data.assignedAt(),
                    data.expiresAt(),
                    data.isActive()
            );
        }
    }
}
