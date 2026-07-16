package biz.ugur.busroutebackend.prediction.core;

import java.util.List;

public interface RouteLine {

    String TOPOLOGY_THERE_AND_BACK = "there-and-back";
    String TOPOLOGY_LOOP = "loop";

    record StopPoint(String stopId, int seq, double sMeters) {}

    record TerminalZone(double lat, double lon, double radiusMeters) {}

    record Projection(double s, double distMeters) {}

    String routeNumber();

    int direction();

    String topology();

    double totalMeters();

    List<double[]> points();

    double[] cumDist();

    List<StopPoint> stops();

    TerminalZone terminalZone();

    double[] pointAtS(double s);

    Projection projectOntoRange(double lat, double lon, double sFrom, double sTo, double sDefault);

    default boolean isLoop() {
        return TOPOLOGY_LOOP.equals(topology());
    }

    default double lengthMeters() {
        return totalMeters();
    }

    default double stopS(String stopId) {
        for (StopPoint sp : stops()) {
            if (sp.stopId().equals(stopId)) return sp.sMeters();
        }
        return Double.NaN;
    }

    default double courseAt(double s) {
        double[] cum = cumDist();
        List<double[]> pts = points();
        for (int i = 0; i < cum.length - 1; i++) {
            if (cum[i + 1] >= s) {
                double[] a = pts.get(i);
                double[] b = pts.get(i + 1);
                double mLon = 111320.0 * Math.cos(Math.toRadians((a[0] + b[0]) / 2));
                double dx = (b[1] - a[1]) * mLon;
                double dy = (b[0] - a[0]) * 111320.0;
                return (Math.toDegrees(Math.atan2(dx, dy)) + 360.0) % 360.0;
            }
        }
        return 0.0;
    }

    static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }
}
