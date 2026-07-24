package biz.ugur.busroutebackend.transport.domain.valueobject;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

@Value
@Builder(toBuilder = true)
public class TerminalDwellStat {

    String routeId;
    String routeNumber;
    int direction;
    int hourOfDay;
    boolean weekend;

    double avgDwellSeconds;
    long sampleCount;
    Instant lastObservedAt;

    public TerminalDwellStat withNewSample(double dwellSeconds, Instant observedAt) {
        long newCount = sampleCount + 1;
        double newAvg = avgDwellSeconds + (dwellSeconds - avgDwellSeconds) / newCount;
        return this.toBuilder()
                .avgDwellSeconds(newAvg)
                .sampleCount(newCount)
                .lastObservedAt(observedAt)
                .build();
    }

    public static TerminalDwellStat initial(String routeId, String routeNumber, int direction,
                                            int hourOfDay, boolean weekend) {
        return TerminalDwellStat.builder()
                .routeId(routeId)
                .routeNumber(routeNumber)
                .direction(direction)
                .hourOfDay(hourOfDay)
                .weekend(weekend)
                .avgDwellSeconds(0.0)
                .sampleCount(0)
                .build();
    }
}
