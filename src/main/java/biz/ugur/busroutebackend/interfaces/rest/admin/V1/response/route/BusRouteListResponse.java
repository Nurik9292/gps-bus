package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.route;

import biz.ugur.busroutebackend.shared.application.dto.PaginationInfo;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteList;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@ToString
@EqualsAndHashCode
public class BusRouteListResponse {

    @JsonProperty("routes")
    private final List<BusRouteResponse> routes;

    @JsonProperty("active_count")
    private final Long activeCount;

    @JsonProperty("pagination")
    private final PaginationInfo pagination;

    public BusRouteListResponse(List<BusRouteResponse> routes, Long activeCount, PaginationInfo pagination) {
        this.routes = routes;
        this.activeCount = activeCount;
        this.pagination = pagination;
    }

 
    public static BusRouteListResponse fromResult(RouteList routeList) {
        return new BusRouteListResponse(
                routeList.getRoutes()
                        .stream()
                        .map(BusRouteResponse::fromResult)
                        .toList(),
                routeList.getActiveCount(),
                routeList.getPagination()
        );
    }
}