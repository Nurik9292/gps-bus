package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.dto.StopsContext;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.routing.domain.valueobjects.Location;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@Service
@Slf4j
public class NearbyStopsService {

    private final RouteCalculationService routeCalculationService;

    private static final double SEARCH_RADIUS_KM = 1.5;
    private static final int MAX_STOPS_PER_LOCATION = 50;

    private static final double[] LAYERED_SEARCH_RADIUSES = {0.3, 0.6, 1.0};
    private static final int[] MAX_STOPS_PER_LAYER = {4, 6, 8};

    public NearbyStopsService(RouteCalculationService routeCalculationService) {
        this.routeCalculationService = routeCalculationService;
    }

    public Mono<StopsContext> findStopsForBothLocations(SearchContext context) {
        return Mono.zip(
                        findNearbyStops(context.fromLocation(), "origin"),
                        findNearbyStops(context.toLocation(), "destination")
                ).map(tuple -> new StopsContext(tuple.getT1(), tuple.getT2()))
                .doOnNext(stopsContext -> logStopsFound(context, stopsContext));
    }

    public Mono<MultiLayerStopsContext> findMultipleNearbyStops(SearchContext context) {
        return Mono.zip(
                        findLayeredStops(context.fromLocation(), "origin"),
                        findLayeredStops(context.toLocation(), "destination")
                ).map(tuple -> new MultiLayerStopsContext(tuple.getT1(), tuple.getT2()))
                .doOnNext(multiStops -> logMultiLayerStopsFound(context, multiStops));
    }

    private Mono<List<List<BusStop>>> findLayeredStops(Location location, String locationType) {
        List<Mono<List<BusStop>>> layerSearches = IntStream.range(0, LAYERED_SEARCH_RADIUSES.length)
                .mapToObj(i -> findStopsInRadius(location, LAYERED_SEARCH_RADIUSES[i], MAX_STOPS_PER_LAYER[i], locationType))
                .toList();

        return Mono.zip(layerSearches, layers -> {
            List<List<BusStop>> result = new ArrayList<>();
            for (Object layer : layers) {
                @SuppressWarnings("unchecked")
                List<BusStop> typedLayer = (List<BusStop>) layer;
                result.add(typedLayer);
            }
            return result;
        });
    }

    private Mono<List<BusStop>> findStopsInRadius(Location location, double radiusKm, int maxStops, String locationType) {
        return routeCalculationService.findNearbyStops(location, radiusKm)
                .filter(this::isStopAccessible)
                .sort((stop1, stop2) -> compareStopsByEnhancedPriority(stop1, stop2, location))
                .take(maxStops)
                .collectList()
                .doOnNext(stops -> log.debug("Found {} {} stops in radius {}km",
                        stops.size(), locationType, radiusKm));
    }

    private Mono<List<BusStop>> findNearbyStops(Location location, String locationType) {
        log.debug("Finding {} stops near ({}, {}) within {}km",
                locationType, location.getLatitude(), location.getLongitude(), SEARCH_RADIUS_KM);

        return routeCalculationService.findNearbyStops(location, SEARCH_RADIUS_KM)
                .filter(this::isStopAccessible)
                .sort((stop1, stop2) -> compareStopsByEnhancedPriority(stop1, stop2, location))
                .take(MAX_STOPS_PER_LOCATION)
                .collectList()
                .doOnNext(stops -> {
                    log.info("Found {} {} stops near ({}, {})", stops.size(), locationType,
                            location.getLatitude(), location.getLongitude());
                    stops.forEach(stop -> {
                        double distance = location.distanceTo(stop.getLatitude().doubleValue(), stop.getLongitude().doubleValue());
                        log.info("STOP DEBUG: {} ({}) at ({}, {}) - distance: {:.0f}m, major: {}, routes: {}",
                                stop.getId().getValue(), stop.getStopName(),
                                stop.getLatitude(), stop.getLongitude(),
                                distance, stop.getIsMajorStop(), stop.getServingRoutesCount());
                    });
                });
    }

    private int compareStopsByEnhancedPriority(BusStop stop1, BusStop stop2, Location location) {
        // Главный приоритет - ТОЛЬКО расстояние
        double dist1 = location.distanceTo(
                stop1.getLatitude().doubleValue(),
                stop1.getLongitude().doubleValue()
        );
        double dist2 = location.distanceTo(
                stop2.getLatitude().doubleValue(),
                stop2.getLongitude().doubleValue()
        );

        // Всегда сортируем ТОЛЬКО по расстоянию
        return Double.compare(dist1, dist2);
    }

    private int getEstimatedRouteCount(BusStop stop) {
        return stop.getServingRoutesCount();
    }

    private boolean isStopAccessible(BusStop stop) {
        return stop.getIsActive() &&
                stop.getLatitude() != null &&
                stop.getLongitude() != null;
    }

    private void logStopsFound(SearchContext context, StopsContext stopsContext) {
        log.debug("[{}] Found {} origin and {} destination stops",
                context.searchId(),
                stopsContext.fromStops().size(),
                stopsContext.toStops().size());
    }

    private void logMultiLayerStopsFound(SearchContext context, MultiLayerStopsContext multiStops) {
        log.debug("[{}] Found multi-layer stops: {} layers total",
                context.searchId(),
                multiStops.getLayerCount());

        for (int i = 0; i < multiStops.getLayerCount(); i++) {
            StopsContext layer = multiStops.getLayer(i);
            log.debug("[{}] Layer {}: {} origin + {} destination stops (radius: {}km)",
                    context.searchId(), i,
                    layer.fromStops().size(),
                    layer.toStops().size(),
                    i < LAYERED_SEARCH_RADIUSES.length ? LAYERED_SEARCH_RADIUSES[i] : 1.0);
        }
    }

    public static class MultiLayerStopsContext {
        private final List<StopsContext> layers;

        public MultiLayerStopsContext(List<List<BusStop>> fromStopLayers,
                                      List<List<BusStop>> toStopLayers) {
            this.layers = IntStream.range(0, Math.min(fromStopLayers.size(), toStopLayers.size()))
                    .mapToObj(i -> new StopsContext(fromStopLayers.get(i), toStopLayers.get(i)))
                    .toList();
        }

        public StopsContext getLayer(int layerIndex) {
            return layerIndex < layers.size() ? layers.get(layerIndex) : StopsContext.empty();
        }

        public int getLayerCount() {
            return layers.size();
        }

        public boolean hasInsufficientStops() {
            return layers.isEmpty() ||
                    layers.getFirst().hasInsufficientStops();
        }

        public String getDebugInfo() {
            StringBuilder sb = new StringBuilder();
            sb.append("MultiLayerStopsContext{layers=").append(layers.size());
            for (int i = 0; i < layers.size(); i++) {
                StopsContext layer = layers.get(i);
                sb.append(", layer").append(i).append("=[from=")
                        .append(layer.fromStops().size())
                        .append(", to=").append(layer.toStops().size()).append("]");
            }
            sb.append("}");
            return sb.toString();
        }
    }
}