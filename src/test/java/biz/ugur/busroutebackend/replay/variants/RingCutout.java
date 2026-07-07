package biz.ugur.busroutebackend.replay.variants;

import biz.ugur.busroutebackend.replay.GeometryFixture;

import java.util.ArrayList;
import java.util.List;

public final class RingCutout {

    public record Bbox(double lonMin, double lonMax, double latMin, double latMax) {

        boolean contains(double[] latLon) {
            return latLon[1] >= lonMin && latLon[1] <= lonMax
                    && latLon[0] >= latMin && latLon[0] <= latMax;
        }
    }

    public record CutResult(GeometryFixture shortVariant, double trunkStartS, double trunkEndS,
                            int stopsDropped) {}

    private RingCutout() {}

    public static CutResult trunkOutsideRingZone(GeometryFixture full, Bbox ringBbox,
                                                 String shortRouteNumber) {
        List<double[]> pts = full.points();
        int bestStart = -1;
        int bestEnd = -1;
        int curStart = -1;
        for (int i = 0; i <= pts.size(); i++) {
            boolean outside = i < pts.size() && !ringBbox.contains(pts.get(i));
            if (outside && curStart < 0) curStart = i;
            if (!outside && curStart >= 0) {
                if (bestStart < 0 || i - curStart > bestEnd - bestStart) {
                    bestStart = curStart;
                    bestEnd = i;
                }
                curStart = -1;
            }
        }
        if (bestStart < 0 || bestEnd - bestStart < 10) {
            throw new IllegalStateException("ствол вне кольцевой зоны не найден");
        }
        List<double[]> trunkPts = new ArrayList<>(pts.subList(bestStart, bestEnd));
        GeometryFixture trunk = GeometryFixture.fromPolyline(shortRouteNumber, full.direction(), trunkPts);

        List<GeometryFixture.StopPoint> stops = new ArrayList<>();
        int seq = 1;
        int dropped = 0;
        for (GeometryFixture.StopPoint sp : full.stops()) {
            double[] pt = full.pointAtS(sp.sMeters());
            var proj = trunk.projectOntoRange(pt[0], pt[1], 0, trunk.totalMeters(), 0);
            if (proj.distMeters() <= 30.0) {
                stops.add(new GeometryFixture.StopPoint(sp.stopId(), seq++, proj.s()));
            } else {
                dropped++;
            }
        }
        return new CutResult(trunk.withStops(List.copyOf(stops)),
                full.cumDist()[bestStart], full.cumDist()[bestEnd - 1], dropped);
    }

    public static CutResult prefixToS(GeometryFixture full, double sTurnMeters,
                                      String shortRouteNumber) {
        List<double[]> pts = full.points();
        double[] cum = full.cumDist();
        List<double[]> outPts = new ArrayList<>();
        for (int i = 0; i < pts.size(); i++) {
            if (cum[i] < sTurnMeters) outPts.add(pts.get(i));
        }
        outPts.add(full.pointAtS(sTurnMeters));
        List<double[]> densified = new ArrayList<>();
        densified.add(outPts.get(0));
        for (int i = 1; i < outPts.size(); i++) {
            double[] a = outPts.get(i - 1);
            double[] b = outPts.get(i);
            double segLen = GeometryFixture.haversineMeters(a[0], a[1], b[0], b[1]);
            int parts = (int) Math.ceil(segLen / 50.0);
            for (int p = 1; p <= parts; p++) {
                densified.add(new double[]{a[0] + (b[0] - a[0]) * p / parts,
                        a[1] + (b[1] - a[1]) * p / parts});
            }
        }
        GeometryFixture cut = GeometryFixture.fromPolyline(shortRouteNumber, full.direction(), densified);

        List<GeometryFixture.StopPoint> stops = new ArrayList<>();
        int seq = 1;
        int dropped = 0;
        for (GeometryFixture.StopPoint sp : full.stops()) {
            if (sp.sMeters() <= sTurnMeters) {
                stops.add(new GeometryFixture.StopPoint(sp.stopId(), seq++, sp.sMeters()));
            } else {
                dropped++;
            }
        }
        return new CutResult(cut.withStops(List.copyOf(stops)), 0, sTurnMeters, dropped);
    }

    public static CutResult suffixFromS(GeometryFixture full, double sTurnMeters,
                                        String shortRouteNumber) {
        List<double[]> pts = full.points();
        double[] cum = full.cumDist();
        List<double[]> outPts = new ArrayList<>();
        double[] start = full.pointAtS(sTurnMeters);
        outPts.add(start);
        for (int i = 0; i < pts.size(); i++) {
            if (cum[i] > sTurnMeters) outPts.add(pts.get(i));
        }
        List<double[]> densified = new ArrayList<>();
        densified.add(outPts.get(0));
        for (int i = 1; i < outPts.size(); i++) {
            double[] a = outPts.get(i - 1);
            double[] b = outPts.get(i);
            double segLen = GeometryFixture.haversineMeters(a[0], a[1], b[0], b[1]);
            int parts = (int) Math.ceil(segLen / 50.0);
            for (int p = 1; p <= parts; p++) {
                densified.add(new double[]{a[0] + (b[0] - a[0]) * p / parts,
                        a[1] + (b[1] - a[1]) * p / parts});
            }
        }
        GeometryFixture cut = GeometryFixture.fromPolyline(shortRouteNumber, full.direction(), densified);

        List<GeometryFixture.StopPoint> stops = new ArrayList<>();
        int seq = 1;
        int dropped = 0;
        for (GeometryFixture.StopPoint sp : full.stops()) {
            if (sp.sMeters() >= sTurnMeters) {
                stops.add(new GeometryFixture.StopPoint(sp.stopId(), seq++, sp.sMeters() - sTurnMeters));
            } else {
                dropped++;
            }
        }
        return new CutResult(cut.withStops(List.copyOf(stops)), sTurnMeters, cum[cum.length - 1], dropped);
    }

    public static boolean isSimple(GeometryFixture g, double toleranceMeters) {
        List<double[]> pts = g.points();
        for (int i = 0; i < pts.size() - 1; i++) {
            for (int j = i + 2; j < pts.size() - 1; j++) {
                if (i == 0 && j == pts.size() - 2) continue;
                if (segmentsIntersect(pts.get(i), pts.get(i + 1), pts.get(j), pts.get(j + 1))) {
                    double sI = g.cumDist()[i];
                    double sJ = g.cumDist()[j];
                    if (Math.abs(sJ - sI) > toleranceMeters) return false;
                }
            }
        }
        return true;
    }

    private static boolean segmentsIntersect(double[] a, double[] b, double[] c, double[] d) {
        return ccw(a, c, d) != ccw(b, c, d) && ccw(a, b, c) != ccw(a, b, d);
    }

    private static boolean ccw(double[] p1, double[] p2, double[] p3) {
        return (p3[0] - p1[0]) * (p2[1] - p1[1]) > (p2[0] - p1[0]) * (p3[1] - p1[1]);
    }
}
