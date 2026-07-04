package biz.ugur.busroutebackend.replay.metrics;

import biz.ugur.busroutebackend.replay.GeometryFixture;

import java.util.List;

public final class MarkerFlightMetric {

    public record FlightStats(double maxRatio, long violations, long sanctionedJumps) {}

    private MarkerFlightMetric() {}

    public static FlightStats compute(List<double[]> broadcastLatLon, List<Double> tSec,
                                      List<Boolean> sanctioned, double vMaxMs, double k) {
        double maxRatio = 0;
        long violations = 0;
        long sanctionedJumps = 0;
        for (int i = 1; i < broadcastLatLon.size(); i++) {
            double dt = Math.max(tSec.get(i) - tSec.get(i - 1), 1.0);
            double dist = GeometryFixture.haversineMeters(
                    broadcastLatLon.get(i - 1)[0], broadcastLatLon.get(i - 1)[1],
                    broadcastLatLon.get(i)[0], broadcastLatLon.get(i)[1]);
            double ratio = dist / (dt * vMaxMs);
            if (sanctioned.get(i) || sanctioned.get(i - 1)) {
                if (ratio > k) sanctionedJumps++;
                continue;
            }
            maxRatio = Math.max(maxRatio, ratio);
            if (ratio > k) violations++;
        }
        return new FlightStats(maxRatio, violations, sanctionedJumps);
    }
}
