package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.assignment;

import biz.ugur.busroutebackend.transport.application.dto.assignment.RouteAssignmentData;

import java.util.List;

public record RouteAssignmentListResponse(
        List<RouteAssignmentResponse> assignments,
        int total
) {
    public static RouteAssignmentListResponse fromData(List<RouteAssignmentData> dataList) {
        List<RouteAssignmentResponse> responses = dataList.stream()
                .map(RouteAssignmentResponse::fromData)
                .toList();

        return new RouteAssignmentListResponse(responses, responses.size());
    }
}
