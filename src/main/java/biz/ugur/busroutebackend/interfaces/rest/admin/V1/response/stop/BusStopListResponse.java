package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.stop;

import biz.ugur.busroutebackend.shared.application.dto.PaginationInfo;
import biz.ugur.busroutebackend.transport.application.dto.stop.StopList;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@ToString
@EqualsAndHashCode
public class BusStopListResponse {

    @JsonProperty("stops")
    private final List<BusStopResponse> stops;

    @JsonProperty("active_count")
    private final Long activeCount;

    @JsonProperty("pagination")
    private final PaginationInfo pagination;

    public BusStopListResponse(List<BusStopResponse> stops, Long activeCount, PaginationInfo pagination) {
        this.stops = stops;
        this.activeCount = activeCount;
        this.pagination = pagination;
    }

    public static BusStopListResponse fromResult(StopList stopList) {
        return new BusStopListResponse(
                stopList.getStops()
                        .stream()
                        .map(BusStopResponse::fromResult)
                        .toList(),
                stopList.getActiveCount(),
                stopList.getPagination()
        );
    }
}
