package biz.ugur.busroutebackend.transport.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class BusRouteListResponse {

    @JsonProperty("routes")
    private List<BusRouteResponse> routes;

    @JsonProperty("total_count")
    private Long totalCount;

    @JsonProperty("page")
    private Integer page;

    @JsonProperty("size")
    private Integer size;

    @JsonProperty("has_next")
    private Boolean hasNext;

    public BusRouteListResponse(List<BusRouteResponse> routes, Long totalCount, Integer page, Integer size) {
        this.routes = routes;
        this.totalCount = totalCount;
        this.page = page;
        this.size = size;
        this.hasNext = (long) (page + 1) * size < totalCount;
    }
}