package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;


@Component
@Slf4j
public class RouteGeometryCache {

  
    public static final String FORWARD  = "_0";

    public static final String BACKWARD = "_1";

    private final ConcurrentHashMap<String, List<double[]>>    pointsCache        = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, double[]>          cumulativeDistancesCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Double>            distanceCache      = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, double[]>          stopFractionsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<RouteStopInfo>> routeStopsCache  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, java.util.Map<String, Double>> stopFractionsByIdCache   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String>            routeNameCache     = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String>            routeColorCache    = new ConcurrentHashMap<>();

    private static final double SEQUENCE_INVERSION_TOLERANCE = 0.002;

    private final BusRouteRepository busRouteRepository;
    private final MapMatchingService mapMatchingService;

    public RouteGeometryCache(BusRouteRepository busRouteRepository, MapMatchingService mapMatchingService) {
        this.busRouteRepository = busRouteRepository;
        this.mapMatchingService = mapMatchingService;
    }

    private volatile boolean loaded = false;

    @PostConstruct
    public void init() {
        loadWithRetry()
                .timeout(Duration.ofSeconds(60))
                .doOnSuccess(v -> {
                    loaded = true;
                    log.info("[GPS_PIPELINE] Route geometry cache loaded: {} geometry entries, {} stop-fraction entries",
                            pointsCache.size(), stopFractionsCache.size());
                })
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .subscribe(
                        null,
                        err -> log.error(
                                "[GPS_PIPELINE] Route geometry cache failed to load — prediction falls back to dead-reckoning: {}",
                                err.getMessage()));
    }

    public boolean isLoaded() {
        return loaded;
    }

    private reactor.core.publisher.Mono<Void> loadWithRetry() {
        return busRouteRepository.findActiveRoutes()
                .doOnNext(this::cacheRoute)
                .flatMap(route -> loadStopFractions(route.getId().getValue()))
                .retryWhen(reactor.util.retry.Retry.backoff(5, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(32))
                        .doBeforeRetry(signal -> log.warn(
                                "[GPS_PIPELINE] Route geometry cache load failed (attempt {}), retrying in {}s: {}",
                                signal.totalRetries() + 1,
                                Math.min(2 << (int) signal.totalRetries(), 32),
                                signal.failure().getMessage())))
                .doOnError(e -> log.error(
                        "[GPS_PIPELINE] Route geometry cache failed after all retries — prediction will use dead-reckoning: {}",
                        e.getMessage()))
                .then();
    }

    private reactor.core.publisher.Mono<Void> loadStopFractions(String routeId) {
        return reactor.core.publisher.Flux.fromIterable(List.of(0, 1))
                .flatMap(dir -> busRouteRepository.getRouteStopsInfoByRouteId(routeId, dir)
                        .collectList()
                        .doOnSuccess(stops -> cacheStopFractions(routeId, dir, stops)))
                .then()
                .onErrorResume(e -> {
                    log.debug("Stop fractions load skipped for route {}: {}", routeId, e.getMessage());
                    return reactor.core.publisher.Mono.empty();
                });
    }

    private void cacheStopFractions(String routeId, int direction, List<RouteStopInfo> stops) {
        String key = routeId + (direction == 0 ? FORWARD : BACKWARD);
        double totalDistance = distanceCache.getOrDefault(key, 0.0);
        if (totalDistance <= 0 || stops == null || stops.isEmpty()) return;

        List<double[]> points = pointsCache.get(key);

        List<StopWithFraction> resolved = stops.stream()
                .map(stop -> resolveStopFraction(points, totalDistance, stop))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingDouble(StopWithFraction::fraction))
                .toList();
        if (resolved.isEmpty()) return;

        double[] fractions = resolved.stream()
                .mapToDouble(StopWithFraction::fraction)
                .toArray();

        stopFractionsCache.put(key, fractions);
        routeStopsCache.put(key, resolved.stream().map(StopWithFraction::stop).toList());

        java.util.Map<String, Double> byId = new java.util.HashMap<>();
        for (StopWithFraction sf : resolved) {
            byId.put(sf.stop().getStopId(), sf.fraction());
        }
        stopFractionsByIdCache.put(key, java.util.Map.copyOf(byId));

        warnOnSequenceOrderInversions(routeId, direction, resolved);
        log.debug("Cached {} stop fractions for routeId {} dir={}", fractions.length, routeId, direction);
    }

    private StopWithFraction resolveStopFraction(List<double[]> points, double totalDistance, RouteStopInfo stop) {
        if (points != null && points.size() >= 2) {
            MapMatchingService.SnappedResult snap = mapMatchingService.snapToNearestSegment(
                    stop.getLatitude().doubleValue(), stop.getLongitude().doubleValue(),
                    points, totalDistance);
            if (snap.snapped()) {
                return new StopWithFraction(stop, snap.fraction());
            }
            log.debug("[GPS_PIPELINE] Stop {} is {}m from route polyline — falling back to legacy distance",
                    stop.getStopId(), (int) snap.distanceMeters());
        }
        Integer legacyDistance = stop.getDistanceFromStartMeters();
        if (legacyDistance != null && legacyDistance >= 0) {
            return new StopWithFraction(stop, legacyDistance / totalDistance);
        }
        return null;
    }

    private void warnOnSequenceOrderInversions(String routeId, int direction, List<StopWithFraction> resolved) {
        List<StopWithFraction> bySequence = resolved.stream()
                .filter(sf -> sf.stop().getSequence() != null)
                .sorted(Comparator.comparingInt(sf -> sf.stop().getSequence()))
                .toList();
        long inversions = 0;
        for (int i = 1; i < bySequence.size(); i++) {
            if (bySequence.get(i).fraction() < bySequence.get(i - 1).fraction() - SEQUENCE_INVERSION_TOLERANCE) {
                inversions++;
            }
        }
        if (inversions > 0) {
            log.warn("[GPS_PIPELINE] Route {} dir={}: {} stop fraction inversions against stop_sequence order — possible out-and-back wrong-pass snap",
                    routeId, direction, inversions);
        }
    }

    private record StopWithFraction(RouteStopInfo stop, double fraction) {}


    public List<double[]> getPoints(String routeId, int direction) {
        String key = routeId + (direction == 0 ? FORWARD : BACKWARD);
        return pointsCache.get(key);
    }

    
    public double getTotalDistance(String routeId, int direction) {
        String key = routeId + (direction == 0 ? FORWARD : BACKWARD);
        return distanceCache.getOrDefault(key, 0.0);
    }

 
    public double[] getStopFractions(String routeId, int direction) {
        String key = routeId + (direction == 0 ? FORWARD : BACKWARD);
        return stopFractionsCache.get(key);
    }

   
    public List<RouteStopInfo> getRouteStops(String routeId, int direction) {
        String key = routeId + (direction == 0 ? FORWARD : BACKWARD);
        List<RouteStopInfo> stops = routeStopsCache.get(key);
        return stops != null ? stops : Collections.emptyList();
    }


    public List<RouteStopInfo> getStopsAhead(String routeId, int direction, double currentFraction) {
        int idx = firstStopIndexAhead(routeId, direction, currentFraction);
        if (idx < 0) return Collections.emptyList();
        List<RouteStopInfo> stops = getRouteStops(routeId, direction);
        if (idx >= stops.size()) return Collections.emptyList();
        return List.copyOf(stops.subList(idx, stops.size()));
    }

    public Optional<RouteStopInfo> getNextStop(String routeId, int direction, double currentFraction) {
        int idx = firstStopIndexAhead(routeId, direction, currentFraction);
        if (idx < 0) return Optional.empty();
        List<RouteStopInfo> stops = getRouteStops(routeId, direction);
        if (idx >= stops.size()) return Optional.empty();
        return Optional.of(stops.get(idx));
    }

    private int firstStopIndexAhead(String routeId, int direction, double currentFraction) {
        if (getTotalDistance(routeId, direction) <= 0) return -1;
        String key = routeId + (direction == 0 ? FORWARD : BACKWARD);
        double[] fractions = stopFractionsCache.get(key);
        if (fractions == null || fractions.length == 0) return -1;

        double threshold = currentFraction + 0.0001;
        int idx = java.util.Arrays.binarySearch(fractions, threshold);
        return idx >= 0 ? idx + 1 : -idx - 1;
    }

 
    public OptionalDouble getStopFraction(String routeId, int direction, String stopId) {
        if (stopId == null) return OptionalDouble.empty();
        String key = routeId + (direction == 0 ? FORWARD : BACKWARD);
        java.util.Map<String, Double> byId = stopFractionsByIdCache.get(key);
        if (byId == null) return OptionalDouble.empty();
        Double frac = byId.get(stopId);
        return frac != null ? OptionalDouble.of(frac) : OptionalDouble.empty();
    }


    public reactor.core.publisher.Mono<Void> refreshRoute(String routeIdOrNumber) {
        return busRouteRepository.findById(biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId.of(routeIdOrNumber))
                .switchIfEmpty(reactor.core.publisher.Mono.defer(
                        () -> busRouteRepository.findPreferredByRouteNumber(routeIdOrNumber)))
                .filter(BusRoute::getIsActive)
                .doOnNext(this::cacheRoute)
                .flatMap(route -> loadStopFractions(route.getId().getValue()))
                .doOnSuccess(ignored -> log.info("[GPS_PIPELINE] Refreshed route geometry cache for {}", routeIdOrNumber))
                .doOnError(error -> log.error("[GPS_PIPELINE] Failed to refresh route {}: {}", routeIdOrNumber, error.getMessage()))
                .then();
    }


    private void cacheRoute(BusRoute route) {
        String routeId = route.getId().getValue();
        if (route.getRouteName() != null) {
            routeNameCache.put(routeId, route.getRouteName());
        }
        if (route.getRouteColor() != null) {
            routeColorCache.put(routeId, route.getRouteColor());
        }
        cacheGeometry(routeId, FORWARD,  route.getRouteGeometryForward());
        cacheGeometry(routeId, BACKWARD, route.getRouteGeometryBackward());
    }

    public String getRouteName(String routeId) {
        return routeNameCache.get(routeId);
    }

    public String getRouteColor(String routeId) {
        return routeColorCache.get(routeId);
    }

    private void cacheGeometry(String routeId, String suffix, String wkt) {
        if (wkt == null || wkt.isBlank()) return;

        try {
            List<double[]> points = parseWkt(wkt);
            if (points.size() < 2) return;

            String key = routeId + suffix;
            pointsCache.put(key, points);

            double[] cumDist = new double[points.size()];
            cumDist[0] = 0;
            for (int i = 1; i < points.size(); i++) {
                cumDist[i] = cumDist[i - 1] + DistanceCalculationService.haversineDistanceMeters(
                        points.get(i - 1)[0], points.get(i - 1)[1],
                        points.get(i)[0], points.get(i)[1]);
            }
            cumulativeDistancesCache.put(key, cumDist);
            distanceCache.put(key, cumDist[cumDist.length - 1]);
        } catch (Exception e) {
            log.warn("[GPS_PIPELINE] Cannot parse WKT for route {} {}: {}", routeId, suffix, e.getMessage());
        }
    }

    public double[] getCumulativeDistances(String routeId, int direction) {
        String key = routeId + (direction == 0 ? FORWARD : BACKWARD);
        return cumulativeDistancesCache.get(key);
    }

   
    static List<double[]> parseWkt(String wkt) {
        String trimmed = wkt.trim();
        if (!trimmed.startsWith("LINESTRING(") || !trimmed.endsWith(")")) {
            throw new IllegalArgumentException("Expected LINESTRING(...): " + trimmed);
        }
        String inner = trimmed.substring(11, trimmed.length() - 1).trim();
        String[] tokens = inner.split(",");
        double[][] result = new double[tokens.length][2];
        for (int i = 0; i < tokens.length; i++) {
            String[] parts = tokens[i].trim().split("\\s+");
            if (parts.length < 2) throw new IllegalArgumentException("Bad point: " + tokens[i]);
            double lon = Double.parseDouble(parts[0]);
            double lat = Double.parseDouble(parts[1]);
            result[i] = new double[]{lat, lon}; 
        }
        return List.of(result);
    }

}
