package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.route;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Lightweight DTO for route select boxes.
 * Contains only essential fields needed for dropdowns.
 */
public record RouteSelectOption(
        @JsonProperty("id")
        String id,

        @JsonProperty("route_number")
        String routeNumber,

        @JsonProperty("route_name")
        String routeName
) {
}
