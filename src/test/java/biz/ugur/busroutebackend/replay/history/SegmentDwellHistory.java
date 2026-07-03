package biz.ugur.busroutebackend.replay.history;

import java.util.Map;
import java.util.OptionalDouble;

public record SegmentDwellHistory(
        Map<String, Stat> dwellByStop,
        Map<String, Stat> segTravelByKey) {

    public record Stat(double meanSec, int n) {}

    public SegmentDwellHistory {
        dwellByStop = Map.copyOf(dwellByStop);
        segTravelByKey = Map.copyOf(segTravelByKey);
    }

    public static SegmentDwellHistory empty() {
        return new SegmentDwellHistory(Map.of(), Map.of());
    }

    public static String segKey(String fromStopId, String toStopId, int hourBin, boolean weekend) {
        return fromStopId + "|" + toStopId + "|h" + hourBin + "|" + (weekend ? "we" : "wd");
    }

    public OptionalDouble dwellSec(String stopId, int nMin) {
        Stat s = dwellByStop.get(stopId);
        return s != null && s.n() >= nMin ? OptionalDouble.of(s.meanSec()) : OptionalDouble.empty();
    }

    public OptionalDouble segTravelSec(String fromStopId, String toStopId,
                                       int hourBin, boolean weekend, int nMin) {
        Stat s = segTravelByKey.get(segKey(fromStopId, toStopId, hourBin, weekend));
        return s != null && s.n() >= nMin ? OptionalDouble.of(s.meanSec()) : OptionalDouble.empty();
    }
}
