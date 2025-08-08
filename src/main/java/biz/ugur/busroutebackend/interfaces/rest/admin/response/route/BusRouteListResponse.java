package biz.ugur.busroutebackend.interfaces.rest.admin.response.route;

import biz.ugur.busroutebackend.transport.application.dto.route.RouteList;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class BusRouteListResponse {

    @JsonProperty("routes")
    private List<BusRouteResponse> routes;

    @JsonProperty("total_count")
    private Integer totalCount;

    @JsonProperty("active_count")
    private Long activeCount;

    public BusRouteListResponse(List<BusRouteResponse> routes, Long activeCount) {
        this.routes = routes;
        this.totalCount = routes.size();
        this.activeCount = activeCount;
    }

    public static BusRouteListResponse fromResult(RouteList routeList) {
        return new  BusRouteListResponse(
                routeList.getRoutes()
                        .stream()
                        .map(BusRouteResponse::fromResult)
                        .toList(),
                routeList.getActiveCount()
        );
    }
}