package biz.ugur.busroutebackend.transport.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class BusStopListResponse {

    @JsonProperty("stops")
    private List<BusStopResponse> stops;

    @JsonProperty("total_count")
    private Long totalCount;

    @JsonProperty("page")
    private Integer page;

    @JsonProperty("size")
    private Integer size;

    @JsonProperty("has_next")
    private Boolean hasNext;

    public BusStopListResponse(List<BusStopResponse> stops, Long totalCount, Integer page, Integer size) {
        this.stops = stops;
        this.totalCount = totalCount;
        this.page = page;
        this.size = size;
        this.hasNext = (long) (page + 1) * size < totalCount;
    }
}