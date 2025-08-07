package biz.ugur.busroutebackend.transport.application.dto.stop;

import lombok.Data;

import java.util.List;

@Data
public class StopList {

    List<StopResult> stops;
    Integer totalCount;
    Long activeCount;

    public StopList(List<StopResult> stops, Long activeCount) {
        this.stops = stops;
        this.totalCount = stops.size();
        this.activeCount = activeCount;
    }
}
