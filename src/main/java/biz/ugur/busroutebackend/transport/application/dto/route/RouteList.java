package biz.ugur.busroutebackend.transport.application.dto.route;

import biz.ugur.busroutebackend.shared.application.dto.PagedList;
import biz.ugur.busroutebackend.shared.application.dto.PaginationInfo;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.List;

/**
 * DTO for bus route list results with pagination support.
 * Part of the Transport bounded context application layer.
 *
 * <p>Following DDD and Clean Architecture principles:
 * <ul>
 *   <li>Application layer DTO (not domain model)</li>
 *   <li>Contains pagination metadata via PaginationInfo value object</li>
 *   <li>Immutable for thread safety</li>
 *   <li>Implements PagedList interface for standardized pagination</li>
 * </ul>
 */
@Getter
@ToString
@EqualsAndHashCode
public final class RouteList implements PagedList<RouteData> {

    private final List<RouteData> routes;
    private final Long activeCount;
    private final PaginationInfo pagination;

    /**
     * Constructor for paginated route list.
     *
     * @param routes List of route data for current page
     * @param activeCount Total number of active routes
     * @param currentPage Current page number (1-based)
     * @param pageSize Number of items per page
     * @param totalItems Total number of items across all pages
     */
    public RouteList(List<RouteData> routes, Long activeCount, int currentPage, int pageSize, long totalItems) {
        this.routes = Collections.unmodifiableList(routes);
        this.activeCount = activeCount;
        this.pagination = PaginationInfo.of(currentPage, pageSize, totalItems);
    }

    /**
     * Factory method for creating RouteList.
     *
     * @param routes List of route data for current page
     * @param activeCount Total number of active routes
     * @param currentPage Current page number (1-based)
     * @param pageSize Number of items per page
     * @param totalItems Total number of items across all pages
     * @return New RouteList instance
     */
    public static RouteList of(
        List<RouteData> routes,
        Long activeCount,
        int currentPage,
        int pageSize,
        long totalItems
    ) {
        return new RouteList(routes, activeCount, currentPage, pageSize, totalItems);
    }

    // PagedList interface implementation

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
