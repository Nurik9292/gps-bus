package biz.ugur.busroutebackend.transport.domain.valueobject;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;


@Value
@Builder(toBuilder = true)
public class StopDwellStat {

    String stopId;
    String routeId;
    String routeNumber;
    int direction;

    double avgDwellSeconds;

    Double minDwellSeconds;

    Double maxDwellSeconds;

    long sampleCount;

    Instant lastDwellAt;

    public StopDwellStat withNewSample(double dwellSeconds, Instant observedAt) {
        long newCount = sampleCount + 1;
        double newAvg = avgDwellSeconds + (dwellSeconds - avgDwellSeconds) / newCount;
        Double newMin = minDwellSeconds == null ? dwellSeconds : Math.min(minDwellSeconds, dwellSeconds);
        Double newMax = maxDwellSeconds == null ? dwellSeconds : Math.max(maxDwellSeconds, dwellSeconds);
        return this.toBuilder()
                .avgDwellSeconds(newAvg)
                .minDwellSeconds(newMin)
                .maxDwellSeconds(newMax)
                .sampleCount(newCount)
                .lastDwellAt(observedAt)
                .build();
    }

    public static StopDwellStat initial(String stopId, String routeId, String routeNumber, int direction) {
        return StopDwellStat.builder()
                .stopId(stopId)
                .routeId(routeId)
                .routeNumber(routeNumber)
                .direction(direction)
                .avgDwellSeconds(15.0)
                .sampleCount(0)
                .build();
    }
}
