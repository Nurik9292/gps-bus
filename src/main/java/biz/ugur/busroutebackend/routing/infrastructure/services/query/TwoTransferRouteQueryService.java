package biz.ugur.busroutebackend.routing.infrastructure.services.query;

import biz.ugur.busroutebackend.routing.domain.repository.RouteSearchRepository;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService.TwoTransferRouteResult;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Two Transfer Route Query Service
 * <p>
 * Infrastructure service for finding routes with two transfers.
 * Delegates to RouteSearchRepository for database access.
 * <p>
 * Responsibilities:
 * - Validation and business logic
 * - Logging and monitoring
 * <p>
 * Does NOT contain SQL - that's in the repository layer.
 * <p>
 * Example: Bus 10 → Transfer at Stop A → Bus 20 → Transfer at Stop B → Bus 30
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TwoTransferRouteQueryService {

    private final RouteSearchRepository routeSearchRepository;

    /**
     * Find routes with two transfers between origin and destination stops
     * <p>
     * Business logic layer - delegates to repository for database access
     *
     * @param fromStops list of origin stops (nearby user's starting location)
     * @param toStops list of destination stops (nearby user's ending location)
     * @param maxTransferDistanceKm maximum allowed walking distance between transfer stops
     * @return flux of route results with two transfers
     */
    public Flux<TwoTransferRouteResult> findRoutesWithTwoTransfers(
            List<BusStop> fromStops,
            List<BusStop> toStops,
            double maxTransferDistanceKm) {

        long startTime = System.currentTimeMillis();

        log.debug("🔍 Two-transfer route search: {} origin stops → {} destination stops (max transfer: {}km)",
                fromStops.size(), toStops.size(), maxTransferDistanceKm);

        if (fromStops.isEmpty() || toStops.isEmpty()) {
            log.warn("❌ Empty stop lists provided");
            return Flux.empty();
        }

        return routeSearchRepository.findTwoTransferRoutes(fromStops, toStops, maxTransferDistanceKm)
                .doOnNext(result -> log.debug("✅ Found two-transfer route: {}-{}-{} ({} + {} + {} minutes)",
                        result.firstRoute().getRouteNumber(),
                        result.secondRoute().getRouteNumber(),
                        result.thirdRoute().getRouteNumber(),
                        result.firstRouteTravelMinutes(),
                        result.secondRouteTravelMinutes(),
                        result.thirdRouteTravelMinutes()))
                .doOnComplete(() -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.debug("✅ Two-transfer route search completed in {}ms", duration);
                })
                .doOnError(error -> log.error("❌ Two-transfer route search failed: {}", error.getMessage(), error));
    }
}
