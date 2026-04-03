package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.domain.repository.BusRouteConnectionRepository;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.routing.infrastructure.services.cache.RouteSearchCacheService;
import biz.ugur.busroutebackend.routing.infrastructure.services.query.DirectRouteQueryService;
import biz.ugur.busroutebackend.routing.infrastructure.services.query.NearbyStopsQueryService;
import biz.ugur.busroutebackend.routing.infrastructure.services.query.OneTransferRouteQueryService;
import biz.ugur.busroutebackend.routing.infrastructure.services.query.TwoTransferRouteQueryService;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;


@Service
@ConditionalOnProperty(name = "routing.dijkstra.enabled", havingValue = "false", matchIfMissing = true)
@Slf4j
@RequiredArgsConstructor
public class GraphRouteCalculationService implements RouteCalculationService {

    private final NearbyStopsQueryService nearbyStopsQueryService;
    private final DirectRouteQueryService directRouteQueryService;
    private final OneTransferRouteQueryService oneTransferQueryService;
    private final TwoTransferRouteQueryService twoTransferQueryService;

    private final RouteSearchCacheService cacheService;

    private final BusRouteConnectionRepository connectionRepository;


    @Override
    public Flux<BusStop> findNearbyStops(Coordinates location, double radiusKm) {
        log.info("Finding stops within {}km of ({}, {})",
                radiusKm, location.getLatitudeAsDouble(), location.getLongitudeAsDouble());
        return nearbyStopsQueryService.findStopsWithinRadius(location, radiusKm);
    }


    @Override
    public Flux<DirectRouteResult> findDirectRoutes(List<BusStop> fromStops, List<BusStop> toStops) {
        long startTime = System.currentTimeMillis();

        log.info("🔍 Finding direct routes: {} origin stops → {} destination stops",
                fromStops.size(), toStops.size());

        if (fromStops.isEmpty() || toStops.isEmpty()) {
            log.warn("❌ Empty stop lists provided");
            return Flux.empty();
        }

        return directRouteQueryService.findDirectRoutes(fromStops, toStops)
                .doOnComplete(() -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("✅ Direct route search completed in {}ms", duration);
                })
                .doOnError(error -> log.error("❌ Direct route search failed: {}", error.getMessage(), error));
    }

    @Override
    public Flux<TransferRouteResult> findRoutesWithOneTransfer(List<BusStop> fromStops,
                                                                List<BusStop> toStops,
                                                                double maxTransferDistanceKm) {
        long startTime = System.currentTimeMillis();

        log.info("🔍 Finding one-transfer routes: {} origin stops → {} destination stops (max transfer: {}km)",
                fromStops.size(), toStops.size(), maxTransferDistanceKm);

        if (fromStops.isEmpty() || toStops.isEmpty()) {
            log.warn("❌ Empty stop lists provided");
            return Flux.empty();
        }

        double adjustedDistance = calculateOptimalTransferDistance(fromStops, toStops, maxTransferDistanceKm);

        log.info("📏 Adjusted transfer distance: {}km → {}km", maxTransferDistanceKm, adjustedDistance);

        return oneTransferQueryService.findRoutesWithOneTransfer(fromStops, toStops, adjustedDistance)
                .doOnComplete(() -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("✅ One-transfer search completed in {}ms", duration);
                })
                .doOnError(error -> log.error("❌ One-transfer search failed: {}", error.getMessage(), error));
    }

    @Override
    public Flux<TwoTransferRouteResult> findRoutesWithTwoTransfers(List<BusStop> fromStops,
                                                                    List<BusStop> toStops,
                                                                    double maxTransferDistanceKm) {
        long startTime = System.currentTimeMillis();

        log.info("🔍 Finding two-transfer routes: {} origin stops → {} destination stops (max transfer: {}km)",
                fromStops.size(), toStops.size(), maxTransferDistanceKm);

        if (fromStops.isEmpty() || toStops.isEmpty()) {
            log.warn("❌ Empty stop lists provided");
            return Flux.empty();
        }

        double adjustedDistance = calculateOptimalTransferDistance(fromStops, toStops, maxTransferDistanceKm);

        log.info("📏 Adjusted transfer distance: {}km → {}km", maxTransferDistanceKm, adjustedDistance);

        return twoTransferQueryService.findRoutesWithTwoTransfers(fromStops, toStops, adjustedDistance)
                .doOnComplete(() -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.info("✅ Two-transfer search completed in {}ms", duration);
                })
                .doOnError(error -> log.error("❌ Two-transfer search failed: {}", error.getMessage(), error));
    }


    @Override
    public Mono<Boolean> areStopsConnected(BusStop stop1, BusStop stop2) {
        BusStopId stopId1 = stop1.getId();
        BusStopId stopId2 = stop2.getId();

        return cacheService.getCachedConnection(stopId1, stopId2)
                .switchIfEmpty(
                        checkStopsConnectionInDatabase(stop1, stop2)
                                .flatMap(connected ->
                                        cacheService.cacheStopsConnection(stopId1, stopId2, connected)
                                                .thenReturn(connected)
                                )
                );
    }



    @Override
    public Flux<BusRoute> getConnectingRoutes(BusStop fromStop, BusStop toStop) {
        return connectionRepository.findConnectingRoutes(fromStop, toStop);
    }


    private Mono<Boolean> checkStopsConnectionInDatabase(BusStop stop1, BusStop stop2) {
        return connectionRepository.areStopsConnected(stop1, stop2);
    }


    private double calculateOptimalTransferDistance(List<BusStop> fromStops, List<BusStop> toStops,
                                                    double maxDistance) {
        if (fromStops.size() > 6 && toStops.size() > 6) {
            double adjusted = Math.min(maxDistance, 0.3);
            log.info("📏 Many stops detected: adjusting transfer distance to {}km", adjusted);
            return adjusted;
        }
        if (fromStops.size() <= 3 || toStops.size() <= 3) {
            double adjusted = Math.min(maxDistance, 0.5);
            log.info("📏 Few stops detected: adjusting transfer distance to {}km", adjusted);
            return adjusted;
        }
        double adjusted = Math.min(maxDistance, 0.4);
        log.info("📏 Medium stops detected: adjusting transfer distance to {}km", adjusted);
        return adjusted;
    }
}
