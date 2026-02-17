package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response;

import biz.ugur.busroutebackend.complaint.application.dto.ComplaintList;
import biz.ugur.busroutebackend.shared.application.dto.PaginationInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

@Getter
public class ComplaintListResponse {

    @JsonProperty("complaints")
    private final List<ComplaintResponse> complaints;

    @JsonProperty("active_count")
    private final Long activeCount;

    @JsonProperty("pagination")
    private final PaginationInfo pagination;

    public ComplaintListResponse(List<ComplaintResponse> complaints, Long activeCount, PaginationInfo pagination) {
        this.complaints = complaints;
        this.activeCount = activeCount;
        this.pagination = pagination;
    }

    public static ComplaintListResponse fromResult(ComplaintList list) {
        return new ComplaintListResponse(
                list.getComplaints().stream().map(ComplaintResponse::fromResult).toList(),
                list.getActiveCount(),
                list.getPagination()
        );
    }
}
