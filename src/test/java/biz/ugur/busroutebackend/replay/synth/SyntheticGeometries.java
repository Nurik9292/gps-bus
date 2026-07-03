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

    public record ForkPair(GeometryFixture full, GeometryFixture shortVariant) {}

    public static ForkPair forkPair(String prefix, double stemMeters,
                                    double fullLegMeters, double shortLegMeters,
                                    double parallelOffsetMeters, double parallelLenMeters) {
        double mLon = METERS_PER_DEG_LAT * Math.cos(Math.toRadians(CENTER_LAT));
        double step = 25.0;
        double baseLat = CENTER_LAT + 0.1;
        double baseLon = CENTER_LON + 0.1;

        List<double[]> fullPts = new ArrayList<>();
        for (double s = 0; s <= stemMeters + fullLegMeters; s += step) {
            fullPts.add(new double[]{baseLat, baseLon + s / mLon});
        }
        GeometryFixture full = GeometryFixture.fromPolyline(prefix + "-full", 0, fullPts)
                .withStops(List.of(
                        new GeometryFixture.StopPoint(prefix + "-stem-stop", 1, stemMeters * 0.5),
                        new GeometryFixture.StopPoint(prefix + "-full-stop", 2, stemMeters + 1500)));

        List<double[]> shortPts = new ArrayList<>();
        for (double s = 0; s <= stemMeters; s += step) {
            shortPts.add(new double[]{baseLat, baseLon + s / mLon});
        }
        double alongEnd = stemMeters + parallelLenMeters;
        for (double s = stemMeters + step; s <= alongEnd; s += step) {
            shortPts.add(new double[]{baseLat + parallelOffsetMeters / METERS_PER_DEG_LAT,
                    baseLon + s / mLon});
        }
        double northLen = shortLegMeters - parallelLenMeters;
        double lonAtTurn = baseLon + alongEnd / mLon;
        for (double n = step; n <= northLen; n += step) {
            shortPts.add(new double[]{
                    baseLat + (parallelOffsetMeters + n) / METERS_PER_DEG_LAT, lonAtTurn});
        }
        GeometryFixture shortG = GeometryFixture.fromPolyline(prefix + "-short", 0, shortPts)
                .withStops(List.of(
                        new GeometryFixture.StopPoint(prefix + "-stem-stop", 1, stemMeters * 0.5),
                        new GeometryFixture.StopPoint(prefix + "-short-stop", 2, stemMeters + 800)));
        return new ForkPair(full, shortG);
    }

    public static GeometryFixture figureEight(String routeNumber) {
        double mLon = METERS_PER_DEG_LAT * Math.cos(Math.toRadians(CENTER_LAT));
        double baseLat = CENTER_LAT + 0.2;
        double baseLon = CENTER_LON + 0.2;
        double step = 25.0;
        List<double[]> pts = new ArrayList<>();
        double[][] waypointsMeters = {
                {0, 0}, {1200, 0}, {1200, 800}, {400, 800}, {400, -400}, {1600, -400}
        };
        double curE = 0;
        double curN = 0;
        pts.add(new double[]{baseLat, baseLon});
        for (int w = 1; w < waypointsMeters.length; w++) {
            double dE = waypointsMeters[w][0] - curE;
            double dN = waypointsMeters[w][1] - curN;
            double len = Math.hypot(dE, dN);
            int n = (int) Math.ceil(len / step);
            for (int i = 1; i <= n; i++) {
                double e = curE + dE * i / n;
                double no = curN + dN * i / n;
                pts.add(new double[]{baseLat + no / METERS_PER_DEG_LAT, baseLon + e / mLon});
            }
            curE = waypointsMeters[w][0];
            curN = waypointsMeters[w][1];
        }
        return GeometryFixture.fromPolyline(routeNumber, 0, pts);
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
