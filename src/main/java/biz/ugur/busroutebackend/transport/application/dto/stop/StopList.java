package biz.ugur.busroutebackend.transport.application.dto.stop;

import lombok.Data;

import java.util.List;

@Data
public class StopList {

    private List<StopData> stops;
    private Integer totalCount;
    private Long activeCount;

    public StopList(List<StopData> stops, Long activeCount) {
        this.stops = stops;
        this.totalCount = stops.size();
        this.activeCount = activeCount;
    }
}
