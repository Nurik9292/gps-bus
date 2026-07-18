package biz.ugur.busroutebackend.prediction.core.history;

import java.util.Map;
import java.util.OptionalDouble;

public record SegmentDwellHistory(
        Map<String, Stat> dwellByStop,
        Map<String, Stat> segTravelByKey,
        Map<String, Double> liveFactorByEdge) {

    public record Stat(double meanSec, int n) {}

    public SegmentDwellHistory {
        dwellByStop = Map.copyOf(dwellByStop);
        segTravelByKey = Map.copyOf(segTravelByKey);
        liveFactorByEdge = Map.copyOf(liveFactorByEdge);
    }

    public SegmentDwellHistory(Map<String, Stat> dwellByStop, Map<String, Stat> segTravelByKey) {
        this(dwellByStop, segTravelByKey, Map.of());
    }

    public static SegmentDwellHistory empty() {
        return new SegmentDwellHistory(Map.of(), Map.of(), Map.of());
    }

    public static String segKey(String fromStopId, String toStopId, int hourBin, boolean weekend) {
        return fromStopId + "|" + toStopId + "|h" + hourBin + "|" + (weekend ? "we" : "wd");
    }

    public static String edgeKey(String fromStopId, String toStopId) {
        return fromStopId + "|" + toStopId;
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

    public double liveFactor(String fromStopId, String toStopId) {
        Double f = liveFactorByEdge.get(edgeKey(fromStopId, toStopId));
        return f == null ? 1.0 : f;
    }
}
