package biz.ugur.busroutebackend.complaint.application.dto;

import biz.ugur.busroutebackend.complaint.domain.model.ComplaintStatus;
import biz.ugur.busroutebackend.complaint.domain.model.ComplaintType;
import lombok.Getter;

@Getter
public class GetAllComplaintsInput {

    private final int page;
    private final int size;
    private final String sort;
    private final String order;
    private final ComplaintStatus status;
    private final ComplaintType type;

    private GetAllComplaintsInput(int page, int size, String sort, String order,
                                   ComplaintStatus status, ComplaintType type) {
        this.page = page;
        this.size = size;
        this.sort = sort;
        this.order = order;
        this.status = status;
        this.type = type;
    }

    public static GetAllComplaintsInput fromParams(int page, int size, String sort, String order,
                                                    String status, String type) {
        ComplaintStatus parsedStatus = status != null && !status.isBlank()
                ? ComplaintStatus.valueOf(status.toUpperCase()) : null;
        ComplaintType parsedType = type != null && !type.isBlank()
                ? ComplaintType.valueOf(type.toUpperCase()) : null;
        return new GetAllComplaintsInput(page, size, sort, order, parsedStatus, parsedType);
    }
}
