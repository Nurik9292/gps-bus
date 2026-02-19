package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ODPairsResponse(
        @JsonProperty("pairs") List<ODPairResponse> pairs
) {

    public record ODPairResponse(
            @JsonProperty("from_lat") double fromLat,
            @JsonProperty("from_lon") double fromLon,
            @JsonProperty("to_lat") double toLat,
            @JsonProperty("to_lon") double toLon,
            @JsonProperty("trip_count") int tripCount,
            @JsonProperty("avg_options") double avgOptions
    ) {}
}
