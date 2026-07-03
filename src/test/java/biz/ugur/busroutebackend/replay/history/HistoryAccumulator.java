package biz.ugur.busroutebackend.replay.history;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import biz.ugur.busroutebackend.replay.GpsFix;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HistoryAccumulator {

    public record Config(double arrZoneMeters, double arrSpeedKmh, double departSpeedKmh, double dwellMaxSec) {

        public static Config p2Defaults() {
            return new Config(50.0, 5.0, 5.0, 600.0);
        }
    }

    private final Config cfg;
    private final Map<String, List<Double>> dwellSamples = new HashMap<>();
    private final Map<String, List<Double>> segSamples = new HashMap<>();

    public HistoryAccumulator(Config cfg) {
        this.cfg = cfg;
    }

    public void addRun(List<GpsFix> fixes, GeometryFixture g) {
        int fi = 0;
        Instant prevDepart = null;
        String prevStopId = null;
        for (GeometryFixture.StopPoint sp : g.stops()) {
            double[] pt = g.pointAtS(sp.sMeters());
            int arrivalIdx = -1;
            for (int i = fi; i < fixes.size(); i++) {
                GpsFix f = fixes.get(i);
                double d = GeometryFixture.haversineMeters(f.latitude(), f.longitude(), pt[0], pt[1]);
                if (d <= cfg.arrZoneMeters() && f.speedKmh() < cfg.arrSpeedKmh()) {
                    arrivalIdx = i;
                    break;
                }
            }
            if (arrivalIdx < 0) {
                prevDepart = null;
                prevStopId = null;
                continue;
            }
            int departIdx = arrivalIdx;
            for (int i = arrivalIdx + 1; i < fixes.size(); i++) {
                GpsFix f = fixes.get(i);
                double d = GeometryFixture.haversineMeters(f.latitude(), f.longitude(), pt[0], pt[1]);
                departIdx = i;
                if (d > cfg.arrZoneMeters() || f.speedKmh() >= cfg.departSpeedKmh()) break;
            }
            Instant arrivedAt = fixes.get(arrivalIdx).timestamp();
            Instant departedAt = fixes.get(departIdx).timestamp();
            double dwellSec = (departedAt.toEpochMilli() - arrivedAt.toEpochMilli()) / 1000.0;
            if (dwellSec <= cfg.dwellMaxSec()) {
                dwellSamples.computeIfAbsent(sp.stopId(), k -> new ArrayList<>()).add(dwellSec);
            }
            if (prevDepart != null && prevStopId != null) {
                double segSec = (arrivedAt.toEpochMilli() - prevDepart.toEpochMilli()) / 1000.0;
                if (segSec > 0) {
                    var zdt = arrivedAt.atZone(ZoneOffset.UTC);
                    String key = SegmentDwellHistory.segKey(prevStopId, sp.stopId(),
                            zdt.getHour(), zdt.getDayOfWeek().getValue() >= 6);
                    segSamples.computeIfAbsent(key, k -> new ArrayList<>()).add(segSec);
                }
            }
            prevDepart = departedAt;
            prevStopId = sp.stopId();
            fi = departIdx;
        }
    }

    public SegmentDwellHistory build() {
        Map<String, SegmentDwellHistory.Stat> dwell = new HashMap<>();
        dwellSamples.forEach((stopId, xs) -> dwell.put(stopId, stat(xs)));
        Map<String, SegmentDwellHistory.Stat> seg = new HashMap<>();
        segSamples.forEach((key, xs) -> seg.put(key, stat(xs)));
        return new SegmentDwellHistory(dwell, seg);
    }

    private static SegmentDwellHistory.Stat stat(List<Double> xs) {
        double mean = xs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return new SegmentDwellHistory.Stat(mean, xs.size());
    }
}
