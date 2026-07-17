package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.route;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RouteSelectOption(
        @JsonProperty("id")
        String id,

        @JsonProperty("route_number")
        String routeNumber,

        @JsonProperty("route_name")
        String routeName,

        @JsonProperty("city_id")
        String cityId,

        @JsonProperty("city_name")
        String cityName
) {
}
