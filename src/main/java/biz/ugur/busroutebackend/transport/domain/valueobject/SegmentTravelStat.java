package biz.ugur.busroutebackend.transport.domain.valueobject;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder(toBuilder = true)
public class SegmentTravelStat {

    String routeId;
    String routeNumber;
    int direction;
    String fromStopId;
    String toStopId;
    int hourOfDay;
    boolean weekend;

    double avgTravelSeconds;
    long sampleCount;
    Instant lastObservedAt;

    public SegmentTravelStat withNewSample(double travelSeconds, Instant observedAt) {
        long newCount = sampleCount + 1;
        double newAvg = avgTravelSeconds + (travelSeconds - avgTravelSeconds) / newCount;
        return this.toBuilder()
                .avgTravelSeconds(newAvg)
                .sampleCount(newCount)
                .lastObservedAt(observedAt)
                .build();
    }

    public static SegmentTravelStat initial(String routeId, String routeNumber, int direction,
                                            String fromStopId, String toStopId,
                                            int hourOfDay, boolean weekend) {
        return SegmentTravelStat.builder()
                .routeId(routeId)
                .routeNumber(routeNumber)
                .direction(direction)
                .fromStopId(fromStopId)
                .toStopId(toStopId)
                .hourOfDay(hourOfDay)
                .weekend(weekend)
                .avgTravelSeconds(0.0)
                .sampleCount(0)
                .build();
    }
}
