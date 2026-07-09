package biz.ugur.busroutebackend.replay.models;

import biz.ugur.busroutebackend.prediction.core.GeometryFixture;
import biz.ugur.busroutebackend.prediction.core.GpsFix;
import biz.ugur.busroutebackend.prediction.core.PredictionModel;

public class GeometricSnapModel implements PredictionModel {

    public static final double DEFAULT_MEASUREMENT_VARIANCE_S = 15.0 * 15.0;

    @Override
    public Estimate onFix(GpsFix fix, GeometryFixture g) {
        double best = Double.MAX_VALUE;
        double bestS = 0;
        var pts = g.points();
        double[] cum = g.cumDist();
        for (int i = 0; i < pts.size() - 1; i++) {
            double[] a = pts.get(i);
            double[] b = pts.get(i + 1);
            double[] proj = projectOnSegment(fix.latitude(), fix.longitude(), a, b);
            double d = GeometryFixture.haversineMeters(fix.latitude(), fix.longitude(), proj[0], proj[1]);
            if (d < best) {
                best = d;
                bestS = cum[i] + proj[2] * (cum[i + 1] - cum[i]);
            }
        }
        return new Estimate(bestS, fix.speedKmh() / 3.6, "SNAP", DEFAULT_MEASUREMENT_VARIANCE_S);
    }

    private static double[] projectOnSegment(double lat, double lon, double[] a, double[] b) {
        double mLat = 111320.0;
        double mLon = 111320.0 * Math.cos(Math.toRadians((a[0] + b[0]) / 2));
        double dx = (b[1] - a[1]) * mLon;
        double dy = (b[0] - a[0]) * mLat;
        double l2 = dx * dx + dy * dy;
        double t = 0;
        if (l2 > 0) {
            double px = (lon - a[1]) * mLon;
            double py = (lat - a[0]) * mLat;
            t = Math.max(0, Math.min(1, (px * dx + py * dy) / l2));
        }
        return new double[]{a[0] + t * (b[0] - a[0]), a[1] + t * (b[1] - a[1]), t};
    }
}
