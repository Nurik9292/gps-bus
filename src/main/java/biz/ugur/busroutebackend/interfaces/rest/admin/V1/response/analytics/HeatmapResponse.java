package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record HeatmapResponse(
        @JsonProperty("points") List<HeatmapPointResponse> points,
        @JsonProperty("point_type") String pointType
) {

    public record HeatmapPointResponse(
            @JsonProperty("lat") double lat,
            @JsonProperty("lon") double lon,
            @JsonProperty("search_count") int searchCount
    ) {}
}
