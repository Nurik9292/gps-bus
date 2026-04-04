package biz.ugur.busroutebackend.transport.application.dto.route;

import biz.ugur.busroutebackend.shared.application.dto.PagedList;
import biz.ugur.busroutebackend.shared.application.dto.PaginationInfo;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.List;


@Getter
@ToString
@EqualsAndHashCode
public final class RouteList implements PagedList<RouteData> {

    private final List<RouteData> routes;
    private final Long activeCount;
    private final PaginationInfo pagination;

    public RouteList(List<RouteData> routes, Long activeCount, int currentPage, int pageSize, long totalItems) {
        this.routes = Collections.unmodifiableList(routes);
        this.activeCount = activeCount;
        this.pagination = PaginationInfo.of(currentPage, pageSize, totalItems);
    }

   
    public static RouteList of(
        List<RouteData> routes,
        Long activeCount,
        int currentPage,
        int pageSize,
        long totalItems
    ) {
        return new RouteList(routes, activeCount, currentPage, pageSize, totalItems);
    }

    @Override
    public List<RouteData> items() {
        return routes;
    }

    @Override
    public Long activeCount() {
        return activeCount;
    }

    @Override
    public PaginationInfo pagination() {
        return pagination;
    }
}
