package biz.ugur.busroutebackend.replay.synth;

import biz.ugur.busroutebackend.replay.GeometryFixture;

import java.util.ArrayList;
import java.util.List;

public final class SyntheticGeometries {

    private static final double CENTER_LAT = 37.95;
    private static final double CENTER_LON = 58.38;
    private static final double METERS_PER_DEG_LAT = 111320.0;

    private SyntheticGeometries() {}

    public static GeometryFixture circleLoop(String routeNumber, double approxLengthMeters,
                                             int nPoints, List<Double> stopFractions) {
        double radius = approxLengthMeters / (2 * Math.PI);
        double mLon = METERS_PER_DEG_LAT * Math.cos(Math.toRadians(CENTER_LAT));
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i < nPoints; i++) {
            double theta = 2 * Math.PI * i / nPoints;
            pts.add(new double[]{
                    CENTER_LAT + radius * Math.sin(theta) / METERS_PER_DEG_LAT,
                    CENTER_LON + radius * Math.cos(theta) / mLon});
        }
        pts.add(new double[]{pts.get(0)[0], pts.get(0)[1]});
        GeometryFixture g = GeometryFixture.fromPolyline(routeNumber, 0, pts)
                .withTopology(GeometryFixture.TOPOLOGY_LOOP);
        List<GeometryFixture.StopPoint> stops = new ArrayList<>();
        int seq = 1;
        for (double f : stopFractions) {
            stops.add(new GeometryFixture.StopPoint("loop-stop-" + seq, seq, f * g.totalMeters()));
            seq++;
        }
        return g.withStops(List.copyOf(stops));
    }

    public static GeometryFixture straightLine(String routeNumber, int direction,
                                               double lengthMeters, double pointStepMeters,
                                               List<Double> stopSMeters) {
        double mLon = METERS_PER_DEG_LAT * Math.cos(Math.toRadians(CENTER_LAT));
        List<double[]> pts = new ArrayList<>();
        int n = (int) Math.ceil(lengthMeters / pointStepMeters);
        for (int i = 0; i <= n; i++) {
            double s = Math.min(i * pointStepMeters, lengthMeters);
            double along = direction == 0 ? s : lengthMeters - s;
            pts.add(new double[]{CENTER_LAT + 0.05, CENTER_LON + 0.05 + along / mLon});
        }
        GeometryFixture g = GeometryFixture.fromPolyline(routeNumber, direction, pts);
        List<GeometryFixture.StopPoint> stops = new ArrayList<>();
        int seq = 1;
        for (double s : stopSMeters) {
            stops.add(new GeometryFixture.StopPoint("line-d" + direction + "-stop-" + seq, seq, s));
            seq++;
        }
        return g.withStops(List.copyOf(stops));
    }
}
