package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.dto.SearchResult;
import biz.ugur.busroutebackend.routing.domain.enums.SegmentType;
import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.services.WalkingRouteService;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Builds a "walking only" route option — direct walking from origin to destination
 * without any bus ride. Shown alongside bus routes so the user can compare.
 *
 * <p>Uses OSRM pedestrian routing for accurate street geometry and distance.
 * Falls back to Haversine × 1.3 if OSRM is unavailable.
 *
 * <p>Only offered when the straight-line distance is under MAX_WALKING_ONLY_METERS (3 km).
 */
@Service
@Slf4j
public class WalkingOnlyRouteSearchService {

    private static final double MAX_WALKING_ONLY_METERS = 3000.0;
    private static final double STREET_DISTANCE_FACTOR = 1.3;
    private static final double WALKING_SPEED_M_PER_MIN = 83.33;
    private static final Duration OSRM_TIMEOUT = Duration.ofSeconds(3);

    private final DistanceCalculationService distanceService;
    private final WalkingRouteService walkingRouteService;

    public WalkingOnlyRouteSearchService(DistanceCalculationService distanceService,
                                          WalkingRouteService walkingRouteService) {
        this.distanceService = distanceService;
        this.walkingRouteService = walkingRouteService;
    }

    public Mono<SearchResult> search(SearchContext context) {
        Coordinates from = context.fromLocation();
        Coordinates to = context.toLocation();

        double straightLineMeters = DistanceCalculationService.haversineDistanceMeters(
                from.getLatitudeAsDouble(), from.getLongitudeAsDouble(),
                to.getLatitudeAsDouble(), to.getLongitudeAsDouble());

        if (straightLineMeters > MAX_WALKING_ONLY_METERS) {
            log.debug("[{}] Walking-only: distance {}m > max {}m — skipping",
                    context.searchId(), (int) straightLineMeters, (int) MAX_WALKING_ONLY_METERS);
            return Mono.just(SearchResult.successful("walking_only", List.of()));
        }

        // Try OSRM for accurate street routing, fall back to straight-line estimate
        return walkingRouteService.getWalkingRoute(from, to)
                .timeout(OSRM_TIMEOUT)
                .map(osrmResult -> {
                    int distanceMeters;
                    int walkingMinutes;
                    List<List<Double>> geometry = null;

                    if (osrmResult.hasGeometry() && osrmResult.distanceMeters() > 0) {
                        // OSRM success — use real street distance and geometry
                        distanceMeters = osrmResult.distanceMeters();
                        walkingMinutes = Math.max(1, (int) Math.ceil(distanceMeters / WALKING_SPEED_M_PER_MIN));
                        geometry = osrmResult.coordinates();
                        log.info("[{}] Walking-only (OSRM): {}m street, {} min, {} geometry points",
                                context.searchId(), distanceMeters, walkingMinutes,
                                geometry != null ? geometry.size() : 0);
                    } else {
                        // OSRM returned empty — fallback
                        distanceMeters = (int) (straightLineMeters * STREET_DISTANCE_FACTOR);
                        walkingMinutes = Math.max(1, (int) Math.ceil(distanceMeters / WALKING_SPEED_M_PER_MIN));
                        log.info("[{}] Walking-only (fallback): {}m estimated, {} min",
                                context.searchId(), distanceMeters, walkingMinutes);
                    }

                    return buildResult(from, to, walkingMinutes, distanceMeters, geometry);
                })
                .onErrorResume(e -> {
                    // OSRM unavailable — use straight-line estimate
                    int distanceMeters = (int) (straightLineMeters * STREET_DISTANCE_FACTOR);
                    int walkingMinutes = Math.max(1, (int) Math.ceil(distanceMeters / WALKING_SPEED_M_PER_MIN));
                    log.warn("[{}] Walking-only (OSRM failed: {}): {}m estimated, {} min",
                            context.searchId(), e.getMessage(), distanceMeters, walkingMinutes);
                    return Mono.just(buildResult(from, to, walkingMinutes, distanceMeters, null));
                });
    }

    private SearchResult buildResult(Coordinates from, Coordinates to,
                                      int walkingMinutes, int distanceMeters,
                                      List<List<Double>> geometry) {
        String instruction = String.format("Пешком %d мин (~%d м)", walkingMinutes, distanceMeters);

        RouteSegment walkSegment = (geometry != null && !geometry.isEmpty())
                ? new RouteSegment(SegmentType.WALKING, from, to, walkingMinutes, null, instruction,
                        geometry, distanceMeters)
                : new RouteSegment(SegmentType.WALKING, from, to, walkingMinutes, null, instruction,
                        (String) null, distanceMeters);

        TripOption walkingOption = new TripOption(
                TripType.WALKING_ONLY,
                List.of(walkSegment),
                0,
                LocalDateTime.now()
        );

        return SearchResult.successful("walking_only", List.of(walkingOption));
    }
}
