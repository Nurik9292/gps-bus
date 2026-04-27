package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.dto.SearchResult;
import biz.ugur.busroutebackend.routing.application.factory.TripOptionFactory;
import biz.ugur.busroutebackend.routing.domain.enums.SegmentType;
import biz.ugur.busroutebackend.routing.domain.services.WalkingRouteService;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;


@Service
@Slf4j
public class WalkingOnlyRouteSearchService {

    private static final double MAX_WALKING_ONLY_METERS = 3000.0;
    private static final double STREET_DISTANCE_FACTOR = 1.3;
    private static final double WALKING_SPEED_M_PER_MIN = 83.33;
    private static final Duration OSRM_TIMEOUT = Duration.ofSeconds(3);

    private final DistanceCalculationService distanceService;
    private final WalkingRouteService walkingRouteService;
    private final TripOptionFactory tripOptionFactory;

    public WalkingOnlyRouteSearchService(DistanceCalculationService distanceService,
                                          WalkingRouteService walkingRouteService,
                                          TripOptionFactory tripOptionFactory) {
        this.tripOptionFactory = tripOptionFactory;
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

        return walkingRouteService.getWalkingRoute(from, to)
                .timeout(OSRM_TIMEOUT)
                .map(osrmResult -> {
                    int distanceMeters;
                    int walkingMinutes;
                    List<List<Double>> geometry = null;

                    if (osrmResult.hasGeometry() && osrmResult.distanceMeters() > 0) {
                        distanceMeters = osrmResult.distanceMeters();
                        walkingMinutes = Math.max(1, (int) Math.ceil(distanceMeters / WALKING_SPEED_M_PER_MIN));
                        geometry = osrmResult.coordinates();
                        log.info("[{}] Walking-only (OSRM): {}m street, {} min, {} geometry points",
                                context.searchId(), distanceMeters, walkingMinutes,
                                geometry != null ? geometry.size() : 0);
                    } else {
                        distanceMeters = (int) (straightLineMeters * STREET_DISTANCE_FACTOR);
                        walkingMinutes = Math.max(1, (int) Math.ceil(distanceMeters / WALKING_SPEED_M_PER_MIN));
                        log.info("[{}] Walking-only (fallback): {}m estimated, {} min",
                                context.searchId(), distanceMeters, walkingMinutes);
                    }

                    return buildResult(from, to, walkingMinutes, distanceMeters, geometry);
                })
                .onErrorResume(e -> {
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

        TripOption walkingOption = tripOptionFactory.createWalkingOnlyOption(
                List.of(walkSegment),
                LocalDateTime.now()
        );

        return SearchResult.successful("walking_only", List.of(walkingOption));
    }
}
