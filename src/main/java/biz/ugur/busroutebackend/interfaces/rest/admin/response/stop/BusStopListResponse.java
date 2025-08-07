package biz.ugur.busroutebackend.interfaces.rest.admin.response.stop;

import biz.ugur.busroutebackend.transport.application.dto.stop.StopList;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class BusStopListResponse {

    @JsonProperty("stops")
    private List<BusStopResponse> stops;

    @JsonProperty("total_count")
    private Integer totalCount;

    @JsonProperty("active_count")
    private Long activeCount;

    public BusStopListResponse(List<BusStopResponse> stops, Long activeCount) {
        this.stops = stops;
        this.totalCount = stops.size();
        this.activeCount = activeCount;
    }

    public static BusStopListResponse fromResult(StopList stopList) {
        return new BusStopListResponse(
                stopList.getStops()
                        .stream()
                        .map(BusStopResponse::fromResult)
                        .toList(),
                stopList.getActiveCount()
        );
    }
}
