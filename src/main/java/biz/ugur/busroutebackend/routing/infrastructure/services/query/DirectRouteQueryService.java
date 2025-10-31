package biz.ugur.busroutebackend.routing.infrastructure.services.query;

import biz.ugur.busroutebackend.routing.domain.repository.RouteSearchRepository;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService.DirectRouteResult;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * Direct Route Query Service
 * <p>
 * Infrastructure service for finding direct routes (no transfers).
 * Delegates to RouteSearchRepository for database access.
 * <p>
 * Responsibilities:
 * - Validation and business logic
 * - Enrichment of results
 * - Logging and monitoring
 * <p>
 * Does NOT contain SQL - that's in the repository layer.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DirectRouteQueryService {

    private final RouteSearchRepository routeSearchRepository;


    /**
     * Find all direct routes between origin and destination stops
     * <p>
     * Business logic layer - delegates to repository for database access
     *
     * @param fromStops list of possible origin stops
     * @param toStops list of possible destination stops
     * @return flux of direct route results
     */
    public Flux<DirectRouteResult> findDirectRoutes(List<BusStop> fromStops, List<BusStop> toStops) {
        long startTime = System.currentTimeMillis();

        log.debug("🔍 Direct route search: {} origin stops → {} destination stops",
                fromStops.size(), toStops.size());

        if (fromStops.isEmpty() || toStops.isEmpty()) {
            log.warn("❌ Empty stop lists provided");
            return Flux.empty();
        }

        return routeSearchRepository.findDirectRoutes(fromStops, toStops)
                .doOnNext(result -> log.debug("✅ Found direct route: {} ({} minutes)",
                        result.route().getRouteNumber(),
                        result.estimatedTravelMinutes()))
                .doOnComplete(() -> {
                    long duration = System.currentTimeMillis() - startTime;
                    log.debug("✅ Direct route search completed in {}ms", duration);
                })
                .doOnError(error -> log.error("❌ Direct route search failed: {}", error.getMessage(), error));
    }
}
