package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.dto.SearchResult;
import biz.ugur.busroutebackend.routing.domain.enums.SegmentType;
import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Builds a "walking only" route option — direct walking from origin to destination
 * without any bus ride. Shown alongside bus routes so the user can compare.
 *
 * <p>Only offered when the straight-line distance is under MAX_WALKING_ONLY_METERS (3 km).
 * Uses Haversine distance × 1.3 to approximate real street distance.
 */
@Service
@Slf4j
public class WalkingOnlyRouteSearchService {

    /** Maximum straight-line distance to offer a walking-only option (meters). */
    private static final double MAX_WALKING_ONLY_METERS = 3000.0;

    /** Factor to convert straight-line distance to approximate street distance. */
    private static final double STREET_DISTANCE_FACTOR = 1.3;

    /** Walking speed in m/min (5 km/h = 83.33 m/min). */
    private static final double WALKING_SPEED_M_PER_MIN = 83.33;

    private final DistanceCalculationService distanceService;

    public WalkingOnlyRouteSearchService(DistanceCalculationService distanceService) {
        this.distanceService = distanceService;
    }

    /**
     * Compute a walking-only route if the distance is within limits.
     * Returns a SearchResult with one TripOption of type WALKING_ONLY,
     * or an empty successful result if the distance is too far.
     */
    public Mono<SearchResult> search(SearchContext context) {
        return Mono.fromCallable(() -> {
            Coordinates from = context.fromLocation();
            Coordinates to = context.toLocation();

            double straightLineMeters = DistanceCalculationService.haversineDistanceMeters(
                    from.getLatitudeAsDouble(), from.getLongitudeAsDouble(),
                    to.getLatitudeAsDouble(), to.getLongitudeAsDouble());

            if (straightLineMeters > MAX_WALKING_ONLY_METERS) {
                log.debug("[{}] Walking-only: distance {}m > max {}m — skipping",
                        context.searchId(), (int) straightLineMeters, (int) MAX_WALKING_ONLY_METERS);
                return SearchResult.successful("walking_only", List.of());
            }

            double streetDistanceMeters = straightLineMeters * STREET_DISTANCE_FACTOR;
            int walkingMinutes = Math.max(1, (int) Math.ceil(streetDistanceMeters / WALKING_SPEED_M_PER_MIN));

            String instruction = String.format("Пешком %d мин (~%.0f м)", walkingMinutes, streetDistanceMeters);

            RouteSegment walkSegment = new RouteSegment(
                    SegmentType.WALKING,
                    from,
                    to,
                    walkingMinutes,
                    null,
                    instruction,
                    (String) null,
                    (int) streetDistanceMeters
            );

            TripOption walkingOption = new TripOption(
                    TripType.WALKING_ONLY,
                    List.of(walkSegment),
                    0,
                    LocalDateTime.now()
            );

            log.info("[{}] Walking-only: {}m straight → {}m street → {} min",
                    context.searchId(),
                    (int) straightLineMeters,
                    (int) streetDistanceMeters,
                    walkingMinutes);

            return SearchResult.successful("walking_only", List.of(walkingOption));
        });
    }
}
