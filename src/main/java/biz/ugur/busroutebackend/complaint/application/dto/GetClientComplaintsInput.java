package biz.ugur.busroutebackend.complaint.application.dto;

import lombok.Getter;

@Getter
public class GetClientComplaintsInput {

    private final String clientId;
    private final int page;
    private final int size;
    private final String sort;
    private final String order;

    private GetClientComplaintsInput(String clientId, int page, int size, String sort, String order) {
        this.clientId = clientId;
        this.page = page;
        this.size = size;
        this.sort = sort;
        this.order = order;
    }

    public static GetClientComplaintsInput fromParams(String clientId, int page, int size, String sort, String order) {
        return new GetClientComplaintsInput(clientId, page, size, sort, order);
    }
}
