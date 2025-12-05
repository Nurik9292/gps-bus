package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MobileRouteAlternativesResponse {

    @JsonProperty("primary_route_id")
    private String primaryRouteId;

    @JsonProperty("primary_route_number")
    private String primaryRouteNumber;

    @JsonProperty("primary_route_is_active")
    private Boolean primaryRouteIsActive;

    @JsonProperty("alternatives")
    private List<MobileRouteResponse> alternatives;

    @JsonProperty("alternatives_count")
    private Integer alternativesCount;
}
