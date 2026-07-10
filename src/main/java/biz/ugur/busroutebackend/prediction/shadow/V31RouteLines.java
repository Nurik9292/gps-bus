package biz.ugur.busroutebackend.prediction.shadow;

import biz.ugur.busroutebackend.prediction.core.RouteLine;
import biz.ugur.busroutebackend.prediction.core.RouteTopology;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.RouteGeometryCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class V31RouteLines {

    private static final Logger log = LoggerFactory.getLogger(V31RouteLines.class);
    private static final double ROUND_TRIP_SNAP_TOLERANCE_METERS = 80.0;

    private final RouteGeometryCache cache;
    private final Map<String, RouteTopology> topologies = new ConcurrentHashMap<>();

    public V31RouteLines(RouteGeometryCache cache) {
        this.cache = cache;
    }

    public RouteTopology topologyFor(String routeNumber) {
        return topologies.computeIfAbsent(routeNumber, r -> {
            RouteLine d0 = build(r, 0);
            RouteLine d1 = build(r, 1);
            if (d0 == null || d1 == null) return null;
            return RouteTopology.thereAndBack(d0, d1);
        });
    }

    private RouteLine build(String routeNumber, int direction) {
        List<double[]> points = cache.getPoints(routeNumber, direction);
        if (points == null || points.size() < 2) return null;
        double[] cum = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            cum[i] = cum[i - 1] + RouteLine.haversineMeters(
                    points.get(i - 1)[0], points.get(i - 1)[1],
                    points.get(i)[0], points.get(i)[1]);
        }
        List<RouteLine.StopPoint> stops = new ArrayList<>();
        List<RouteStopInfo> infos = cache.getRouteStops(routeNumber, direction);
        double prevS = -1.0;
        for (RouteStopInfo info : infos == null ? List.<RouteStopInfo>of() : infos) {
            if (info.getLatitude() == null || info.getLongitude() == null) {
                log.warn("v31 s-layer: остановка {} без координат на {}/{} — пропуск (печать, не чинить)",
                        info.getStopId(), routeNumber, direction);
                continue;
            }
            double lat = info.getLatitude().doubleValue();
            double lon = info.getLongitude().doubleValue();
            RouteLine.Projection p = project(points, cum, lat, lon, 0, cum[cum.length - 1]);
            if (p.distMeters() > ROUND_TRIP_SNAP_TOLERANCE_METERS) {
                log.warn("v31 s-layer S-3: остановка {} на {}/{} в {} м от линии — помечена, legacy не подставляется",
                        info.getStopId(), routeNumber, direction, Math.round(p.distMeters()));
            }
            double s = Math.max(0.0, Math.min(p.s(), cum[cum.length - 1]));
            if (s < prevS) {
                throw new IllegalStateException(String.format(
                        "v31 s-layer S-5 ИНВЕРСИЯ stopS: маршрут %s направление %d стоп %s seq %s (s=%.1f < prev %.1f) — СТОП",
                        routeNumber, direction, info.getStopId(), info.getSequence(), s, prevS));
            }
            prevS = s;
            stops.add(new RouteLine.StopPoint(info.getStopId(),
                    info.getSequence() == null ? stops.size() + 1 : info.getSequence(), s));
        }
        return new CacheBackedRouteLine(routeNumber, direction, points, cum, List.copyOf(stops));
    }

    static RouteLine.Projection project(List<double[]> pts, double[] cum,
                                        double lat, double lon, double sFrom, double sTo) {
        double best = Double.MAX_VALUE;
        double bestS = sFrom;
        for (int i = 0; i < pts.size() - 1; i++) {
            if (cum[i + 1] < sFrom || cum[i] > sTo) continue;
            double[] a = pts.get(i);
            double[] b = pts.get(i + 1);
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
            double projLat = a[0] + t * (b[0] - a[0]);
            double projLon = a[1] + t * (b[1] - a[1]);
            double d = RouteLine.haversineMeters(lat, lon, projLat, projLon);
            if (d < best) {
                best = d;
                bestS = cum[i] + t * (cum[i + 1] - cum[i]);
            }
        }
        return new RouteLine.Projection(bestS, best);
    }

    record CacheBackedRouteLine(String routeNumber, int direction, List<double[]> points,
                                double[] cumDistArr,
                                List<RouteLine.StopPoint> stops) implements RouteLine {

        @Override
        public String topology() {
            return TOPOLOGY_THERE_AND_BACK;
        }

        @Override
        public double totalMeters() {
            return cumDistArr[cumDistArr.length - 1];
        }

        @Override
        public double[] cumDist() {
            return cumDistArr;
        }

        @Override
        public TerminalZone terminalZone() {
            return null;
        }

        @Override
        public double[] pointAtS(double s) {
            double clamped = Math.max(0.0, Math.min(s, totalMeters()));
            for (int i = 0; i < cumDistArr.length - 1; i++) {
                if (cumDistArr[i + 1] >= clamped) {
                    double seg = cumDistArr[i + 1] - cumDistArr[i];
                    double t = seg <= 0 ? 0 : (clamped - cumDistArr[i]) / seg;
                    return new double[]{
                            points.get(i)[0] + t * (points.get(i + 1)[0] - points.get(i)[0]),
                            points.get(i)[1] + t * (points.get(i + 1)[1] - points.get(i)[1])};
                }
            }
            return points.get(points.size() - 1);
        }

        @Override
        public Projection projectOntoRange(double lat, double lon,
                                           double sFrom, double sTo, double sDefault) {
            Projection p = project(points, cumDistArr, lat, lon, sFrom, sTo);
            return p.distMeters() == Double.MAX_VALUE
                    ? new Projection(sDefault, Double.MAX_VALUE) : p;
        }
    }
}
