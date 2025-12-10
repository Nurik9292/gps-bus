package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response;

import biz.ugur.busroutebackend.shared.application.dto.PaginationInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MobileRouteListResponse {

    @JsonProperty("routes")
    private List<MobileRouteResponse> routes;

    @JsonProperty("active_count")
    private Long activeCount;

    @JsonProperty("pagination")
    private PaginationInfo pagination;
}
