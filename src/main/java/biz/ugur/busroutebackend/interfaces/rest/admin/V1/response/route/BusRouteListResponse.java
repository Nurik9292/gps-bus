package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.route;

import biz.ugur.busroutebackend.shared.application.dto.PaginationInfo;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteList;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * REST response DTO for bus route list with pagination.
 * Part of the Interfaces layer (REST API).
 *
 * Following Clean Architecture:
 * - Interfaces layer DTO (converts from application layer DTOs)
 * - Contains JSON annotations for API contract
 * - Immutable response object
 */
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

    /**
     * Constructor with pagination support.
     */
    public BusRouteListResponse(List<BusRouteResponse> routes, Long activeCount, PaginationInfo pagination) {
        this.routes = routes;
        this.activeCount = activeCount;
        this.pagination = pagination;
    }

    /**
     * Factory method to create response from application layer DTO.
     *
     * @param routeList The RouteList DTO from application layer
     * @return BusRouteListResponse for REST API
     */
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