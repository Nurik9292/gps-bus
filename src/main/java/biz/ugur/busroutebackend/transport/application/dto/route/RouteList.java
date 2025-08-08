package biz.ugur.busroutebackend.transport.application.dto.route;

import lombok.Data;

import java.util.List;

@Data
public class RouteList {

    private List<RouteResult> routes;
    private Integer totalCount;
    private Long activeCount;


    public  RouteList(List<RouteResult> routes, Long activeCount) {
        this.routes = routes;
        this.totalCount = routes.size();
        this.activeCount = activeCount;
    }

}
