package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.shift;

import biz.ugur.busroutebackend.transport.application.dto.shift.ShiftAssignmentData;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ShiftAssignmentListResponse(
        List<ShiftAssignmentResponse> assignments,
        @JsonProperty("total_count") int totalCount
) {
    public static ShiftAssignmentListResponse fromData(List<ShiftAssignmentData> data) {
        List<ShiftAssignmentResponse> responses = data.stream()
                .map(ShiftAssignmentResponse::fromData)
                .toList();
        return new ShiftAssignmentListResponse(responses, responses.size());
    }
}
