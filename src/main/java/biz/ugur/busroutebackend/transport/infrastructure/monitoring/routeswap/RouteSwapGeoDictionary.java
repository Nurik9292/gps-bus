package biz.ugur.busroutebackend.transport.infrastructure.monitoring.routeswap;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteGeometry;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class RouteSwapGeoDictionary {

    private static final double METERS_PER_DEGREE_LAT = 110_540.0;
    private static final double METERS_PER_DEGREE_LON_AT_EQUATOR = 111_320.0;
    private static final int SEGMENTS_PER_CHUNK = 64;
    private static final int OVERLAP_VERTEX_STRIDE = 2;
    private static final Duration BUILD_TIMEOUT = Duration.ofMinutes(2);

    public record ForeignMatch(String familyKey, double distanceMeters, boolean uniqueVisit) {
    }

    public interface Snapshot {
        boolean knowsAxis(String routeId);

        String familyKeyOf(String routeId);

        String familyLabel(String familyKey);

        String familyRouteNumbers(String familyKey);

        double familyDistanceMeters(String routeId, double lat, double lon);

        double axisDistanceMeters(String routeId, double lat, double lon);

        Optional<ForeignMatch> bestForeign(String assignedRouteId, double lat, double lon);
    }

    private final BusRouteRepository busRouteRepository;
    private final RouteSwapProperties properties;
    private final AtomicBoolean buildInProgress = new AtomicBoolean(false);
    private final java.util.concurrent.atomic.AtomicLong generation = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong lastBuildAttemptMs = new java.util.concurrent.atomic.AtomicLong();
    private volatile BuiltSnapshot snapshot;

    public RouteSwapGeoDictionary(BusRouteRepository busRouteRepository, RouteSwapProperties properties) {
        this.busRouteRepository = busRouteRepository;
        this.properties = properties;
    }

    public Snapshot current() {
        return snapshot;
    }

    public void invalidate() {
        generation.incrementAndGet();
        snapshot = null;
        log.info("[ROUTE_SWAP] geo dictionary invalidated, will rebuild lazily");
    }

    public void ensureBuilt() {
        long now = System.currentTimeMillis();
        long lastAttempt = lastBuildAttemptMs.get();
        if (snapshot != null || now - lastAttempt < 30_000
                || !lastBuildAttemptMs.compareAndSet(lastAttempt, now)
                || !buildInProgress.compareAndSet(false, true)) {
            return;
        }
        snapshot()
                .doFinally(signal -> buildInProgress.set(false))
                .subscribe(
                        built -> {
                        },
                        error -> log.error("[ROUTE_SWAP] geo dictionary build failed", error));
    }

    public Mono<Snapshot> snapshot() {
        BuiltSnapshot existing = snapshot;
        if (existing != null) {
            return Mono.just(existing);
        }
        long buildGeneration = generation.get();
        long startedAtMs = System.currentTimeMillis();
        return busRouteRepository.findActiveRoutes()
                .filter(route -> Boolean.TRUE.equals(route.getIsActive()) && route.hasGeometry())
                .collectList()
                .publishOn(Schedulers.boundedElastic())
                .map(this::build)
                .timeout(BUILD_TIMEOUT)
                .doOnNext(built -> {
                    if (generation.get() != buildGeneration) {
                        log.info("[ROUTE_SWAP] stale geo dictionary build discarded (geometry changed mid-build)");
                        return;
                    }
                    snapshot = built;
                    log.info("[ROUTE_SWAP] geo dictionary built: axes={} families={} elapsedMs={}",
                            built.axes.size(), built.familyLabels.size(),
                            System.currentTimeMillis() - startedAtMs);
                })
                .cast(Snapshot.class);
    }

    private BuiltSnapshot build(List<BusRoute> routes) {
        List<Axis> axes = new ArrayList<>();
        for (BusRoute route : routes) {
            List<Polyline> polylines = new ArrayList<>();
            addPolyline(polylines, route.getForwardGeometry());
            addPolyline(polylines, route.getBackwardGeometry());
            if (!polylines.isEmpty()) {
                axes.add(new Axis(route.getId().getValue(), route.getRouteNumber(), route.getCityId(), polylines));
            }
        }
        Map<String, String> familyOfAxis = assignFamilies(axes);
        Map<String, String> familyLabels = buildFamilyLabels(axes, familyOfAxis);
        Map<String, String> familyRouteNumbers = buildFamilyRouteNumbers(axes, familyOfAxis);
        return new BuiltSnapshot(axes, familyOfAxis, familyLabels, familyRouteNumbers, properties);
    }

    private static void addPolyline(List<Polyline> polylines, RouteGeometry geometry) {
        if (geometry == null || geometry.getPoints() == null || geometry.getPoints().size() < 2) {
            return;
        }
        List<Coordinates> points = geometry.getPoints();
        double[] xs = new double[points.size()];
        double[] ys = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            Coordinates c = points.get(i);
            xs[i] = lonToMeters(c.getLongitudeAsDouble(), c.getLatitudeAsDouble());
            ys[i] = latToMeters(c.getLatitudeAsDouble());
        }
        polylines.add(Polyline.of(xs, ys));
    }

    static double latToMeters(double lat) {
        return lat * METERS_PER_DEGREE_LAT;
    }

    static double lonToMeters(double lon, double lat) {
        return lon * METERS_PER_DEGREE_LON_AT_EQUATOR * Math.cos(Math.toRadians(lat));
    }

    private Map<String, String> assignFamilies(List<Axis> axes) {
        Map<String, Integer> indexById = new HashMap<>();
        for (int i = 0; i < axes.size(); i++) {
            indexById.put(axes.get(i).routeId, i);
        }
        int[] parent = new int[axes.size()];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < axes.size(); i++) {
            for (int j = i + 1; j < axes.size(); j++) {
                Axis a = axes.get(i);
                Axis b = axes.get(j);
                if (!sameCity(a, b) || !a.boundsOverlap(b, properties.getOverlapToleranceMeters())) {
                    continue;
                }
                double shareAb = overlapShare(a, b, properties.getOverlapToleranceMeters());
                if (shareAb < properties.getFamilyMutualShare()) {
                    continue;
                }
                double shareBa = overlapShare(b, a, properties.getOverlapToleranceMeters());
                if (shareBa >= properties.getFamilyMutualShare()) {
                    union(parent, i, j);
                }
            }
        }
        Map<String, String> familyOfAxis = new HashMap<>();
        for (Axis axis : axes) {
            int root = find(parent, indexById.get(axis.routeId));
            familyOfAxis.put(axis.routeId, "family-" + axes.get(root).routeId);
        }
        return familyOfAxis;
    }

    private static boolean sameCity(Axis a, Axis b) {
        return a.cityId != null && a.cityId.equals(b.cityId);
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int i, int j) {
        parent[find(parent, i)] = find(parent, j);
    }

    private static double overlapShare(Axis from, Axis to, double toleranceMeters) {
        int total = 0;
        int close = 0;
        for (Polyline polyline : from.polylines) {
            for (int i = 0; i < polyline.xs.length; i += OVERLAP_VERTEX_STRIDE) {
                total++;
                if (to.distanceMeters(polyline.xs[i], polyline.ys[i], toleranceMeters + 1.0) <= toleranceMeters) {
                    close++;
                }
            }
        }
        return total == 0 ? 0.0 : (double) close / total;
    }

    private static Map<String, String> buildFamilyLabels(List<Axis> axes, Map<String, String> familyOfAxis) {
        Map<String, String> labels = new HashMap<>();
        membersByFamily(axes, familyOfAxis).forEach((familyKey, members) -> labels.put(familyKey,
                String.join("/", members.keySet()) + "@" + members.firstEntry().getValue()));
        return labels;
    }

    private static Map<String, String> buildFamilyRouteNumbers(List<Axis> axes,
                                                               Map<String, String> familyOfAxis) {
        Map<String, String> numbers = new HashMap<>();
        membersByFamily(axes, familyOfAxis).forEach((familyKey, members) ->
                numbers.put(familyKey, String.join("/", members.keySet())));
        return numbers;
    }

    private static Map<String, TreeMap<String, String>> membersByFamily(List<Axis> axes,
                                                                        Map<String, String> familyOfAxis) {
        Map<String, TreeMap<String, String>> membersByFamily = new HashMap<>();
        for (Axis axis : axes) {
            membersByFamily
                    .computeIfAbsent(familyOfAxis.get(axis.routeId), key -> new TreeMap<>())
                    .put(axis.routeNumber, axis.cityId == null ? "?" : axis.cityId);
        }
        return membersByFamily;
    }

    private record Axis(String routeId, String routeNumber, String cityId, List<Polyline> polylines,
                        double minX, double minY, double maxX, double maxY) {

        Axis(String routeId, String routeNumber, String cityId, List<Polyline> polylines) {
            this(routeId, routeNumber, cityId, polylines,
                    polylines.stream().mapToDouble(p -> p.minX).min().orElse(0),
                    polylines.stream().mapToDouble(p -> p.minY).min().orElse(0),
                    polylines.stream().mapToDouble(p -> p.maxX).max().orElse(0),
                    polylines.stream().mapToDouble(p -> p.maxY).max().orElse(0));
        }

        boolean boundsOverlap(Axis other, double margin) {
            return minX - margin <= other.maxX && other.minX - margin <= maxX
                    && minY - margin <= other.maxY && other.minY - margin <= maxY;
        }

        double distanceMeters(double x, double y, double cutoff) {
            double outside = boundsDistance(x, y);
            if (outside > cutoff) {
                return outside;
            }
            double best = Double.POSITIVE_INFINITY;
            for (Polyline polyline : polylines) {
                best = Math.min(best, polyline.distanceMeters(x, y, best));
            }
            return best;
        }

        private double boundsDistance(double x, double y) {
            double dx = Math.max(Math.max(minX - x, 0), x - maxX);
            double dy = Math.max(Math.max(minY - y, 0), y - maxY);
            return Math.hypot(dx, dy);
        }
    }

    private static final class Polyline {
        private final double[] xs;
        private final double[] ys;
        private final double[] chunkMinX;
        private final double[] chunkMinY;
        private final double[] chunkMaxX;
        private final double[] chunkMaxY;
        private final double minX;
        private final double minY;
        private final double maxX;
        private final double maxY;

        private Polyline(double[] xs, double[] ys,
                         double[] chunkMinX, double[] chunkMinY, double[] chunkMaxX, double[] chunkMaxY,
                         double minX, double minY, double maxX, double maxY) {
            this.xs = xs;
            this.ys = ys;
            this.chunkMinX = chunkMinX;
            this.chunkMinY = chunkMinY;
            this.chunkMaxX = chunkMaxX;
            this.chunkMaxY = chunkMaxY;
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        static Polyline of(double[] xs, double[] ys) {
            int segments = xs.length - 1;
            int chunks = (segments + SEGMENTS_PER_CHUNK - 1) / SEGMENTS_PER_CHUNK;
            double[] cMinX = new double[chunks];
            double[] cMinY = new double[chunks];
            double[] cMaxX = new double[chunks];
            double[] cMaxY = new double[chunks];
            for (int c = 0; c < chunks; c++) {
                int from = c * SEGMENTS_PER_CHUNK;
                int to = Math.min(from + SEGMENTS_PER_CHUNK, segments);
                double lminX = Double.POSITIVE_INFINITY;
                double lminY = Double.POSITIVE_INFINITY;
                double lmaxX = Double.NEGATIVE_INFINITY;
                double lmaxY = Double.NEGATIVE_INFINITY;
                for (int i = from; i <= to; i++) {
                    lminX = Math.min(lminX, xs[i]);
                    lminY = Math.min(lminY, ys[i]);
                    lmaxX = Math.max(lmaxX, xs[i]);
                    lmaxY = Math.max(lmaxY, ys[i]);
                }
                cMinX[c] = lminX;
                cMinY[c] = lminY;
                cMaxX[c] = lmaxX;
                cMaxY[c] = lmaxY;
            }
            double minX = Double.POSITIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < xs.length; i++) {
                minX = Math.min(minX, xs[i]);
                minY = Math.min(minY, ys[i]);
                maxX = Math.max(maxX, xs[i]);
                maxY = Math.max(maxY, ys[i]);
            }
            return new Polyline(xs, ys, cMinX, cMinY, cMaxX, cMaxY, minX, minY, maxX, maxY);
        }

        double distanceMeters(double x, double y, double currentBest) {
            double best = currentBest;
            for (int c = 0; c < chunkMinX.length; c++) {
                double dx = Math.max(Math.max(chunkMinX[c] - x, 0), x - chunkMaxX[c]);
                double dy = Math.max(Math.max(chunkMinY[c] - y, 0), y - chunkMaxY[c]);
                if (Math.hypot(dx, dy) >= best) {
                    continue;
                }
                int from = c * SEGMENTS_PER_CHUNK;
                int to = Math.min(from + SEGMENTS_PER_CHUNK, xs.length - 1);
                for (int i = from; i < to; i++) {
                    best = Math.min(best, pointToSegment(x, y, xs[i], ys[i], xs[i + 1], ys[i + 1]));
                }
            }
            return best;
        }

        private static double pointToSegment(double px, double py, double ax, double ay, double bx, double by) {
            double abx = bx - ax;
            double aby = by - ay;
            double lenSq = abx * abx + aby * aby;
            if (lenSq < 1e-9) {
                return Math.hypot(px - ax, py - ay);
            }
            double t = Math.clamp(((px - ax) * abx + (py - ay) * aby) / lenSq, 0.0, 1.0);
            return Math.hypot(px - (ax + t * abx), py - (ay + t * aby));
        }
    }

    private static final class BuiltSnapshot implements Snapshot {
        private final Map<String, Axis> axes;
        private final Map<String, String> familyOfAxis;
        private final Map<String, String> familyLabels;
        private final Map<String, String> familyRouteNumbers;
        private final Map<String, List<Axis>> axesByFamily;
        private final RouteSwapProperties properties;

        private BuiltSnapshot(List<Axis> axisList, Map<String, String> familyOfAxis,
                              Map<String, String> familyLabels, Map<String, String> familyRouteNumbers,
                              RouteSwapProperties properties) {
            this.axes = new HashMap<>();
            this.axesByFamily = new HashMap<>();
            for (Axis axis : axisList) {
                axes.put(axis.routeId, axis);
                axesByFamily.computeIfAbsent(familyOfAxis.get(axis.routeId), key -> new ArrayList<>()).add(axis);
            }
            this.familyOfAxis = familyOfAxis;
            this.familyLabels = familyLabels;
            this.familyRouteNumbers = familyRouteNumbers;
            this.properties = properties;
        }

        @Override
        public String familyRouteNumbers(String familyKey) {
            return familyRouteNumbers.get(familyKey);
        }

        @Override
        public boolean knowsAxis(String routeId) {
            return axes.containsKey(routeId);
        }

        @Override
        public String familyKeyOf(String routeId) {
            return familyOfAxis.get(routeId);
        }

        @Override
        public String familyLabel(String familyKey) {
            return familyLabels.getOrDefault(familyKey, familyKey);
        }

        @Override
        public double familyDistanceMeters(String routeId, double lat, double lon) {
            String familyKey = familyOfAxis.get(routeId);
            if (familyKey == null) {
                return Double.POSITIVE_INFINITY;
            }
            double x = lonToMeters(lon, lat);
            double y = latToMeters(lat);
            double best = Double.POSITIVE_INFINITY;
            for (Axis axis : axesByFamily.get(familyKey)) {
                best = Math.min(best, axis.distanceMeters(x, y, best));
            }
            return best;
        }

        @Override
        public double axisDistanceMeters(String routeId, double lat, double lon) {
            Axis axis = axes.get(routeId);
            if (axis == null) {
                return Double.POSITIVE_INFINITY;
            }
            return axis.distanceMeters(lonToMeters(lon, lat), latToMeters(lat), Double.POSITIVE_INFINITY);
        }

        @Override
        public Optional<ForeignMatch> bestForeign(String assignedRouteId, double lat, double lon) {
            String assignedFamily = familyOfAxis.get(assignedRouteId);
            double x = lonToMeters(lon, lat);
            double y = latToMeters(lat);
            String bestFamily = null;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (Map.Entry<String, List<Axis>> entry : axesByFamily.entrySet()) {
                if (entry.getKey().equals(assignedFamily)) {
                    continue;
                }
                double familyBest = Double.POSITIVE_INFINITY;
                for (Axis axis : entry.getValue()) {
                    familyBest = Math.min(familyBest, axis.distanceMeters(x, y, familyBest));
                }
                if (familyBest < bestDistance) {
                    bestDistance = familyBest;
                    bestFamily = entry.getKey();
                }
            }
            if (bestFamily == null) {
                return Optional.empty();
            }
            boolean unique = bestDistance <= properties.getOnRouteMeters()
                    && clearedFromOtherFamilies(bestFamily, x, y);
            return Optional.of(new ForeignMatch(bestFamily, bestDistance, unique));
        }

        private boolean clearedFromOtherFamilies(String familyKey, double x, double y) {
            double clearance = properties.getCompetitorClearanceMeters();
            for (Map.Entry<String, List<Axis>> entry : axesByFamily.entrySet()) {
                if (entry.getKey().equals(familyKey)) {
                    continue;
                }
                for (Axis axis : entry.getValue()) {
                    if (axis.distanceMeters(x, y, clearance + 1.0) < clearance) {
                        return false;
                    }
                }
            }
            return true;
        }
    }
}
