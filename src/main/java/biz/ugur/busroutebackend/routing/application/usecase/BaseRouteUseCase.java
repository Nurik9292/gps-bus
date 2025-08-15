package biz.ugur.busroutebackend.routing.application.usecase;

import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import biz.ugur.busroutebackend.routing.domain.services.ETACalculationService;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.routing.domain.volumeojects.Location;
import biz.ugur.busroutebackend.routing.domain.volumeojects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.volumeojects.TripOption;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public abstract class BaseRouteUseCase {

    protected final RouteCalculationService routeCalculationService;
    protected final ETACalculationService etaCalculationService;
    protected final EventBus eventBus;

    protected static final class RoutingConstants {
        static final double DEFAULT_NEARBY_RADIUS_KM = 0.8;
        static final int DEFAULT_MAX_STOPS = 8;
        static final int MAX_WALKING_DISTANCE_METERS = 1000;
        static final int MAX_WALKING_TIME_MINUTES = 15;
        static final int MAX_ROUTE_TIME_MINUTES = 120;
        static final int MIN_ROUTE_TIME_MINUTES = 2;
    }

    public BaseRouteUseCase(RouteCalculationService routeCalculationService,
                            ETACalculationService etaCalculationService,
                            EventBus eventBus) {
        this.routeCalculationService = routeCalculationService;
        this.etaCalculationService = etaCalculationService;
        this.eventBus = eventBus;
    }

    protected Mono<List<BusStop>> findNearbyStopsWithLimit(Location location, double radiusKm, int maxStops) {
        return routeCalculationService.findNearbyStops(location, radiusKm)
                .filter(this::isStopAccessible)
                .sort((stop1, stop2) -> compareStopsByPriority(stop1, stop2, location))
                .take(maxStops)
                .collectList()
                .doOnNext(stops -> logNearbyStopsFound(location, stops));
    }

    private boolean isStopAccessible(BusStop stop) {
        return stop.getIsActive() &&
                stop.getLatitude() != null &&
                stop.getLongitude() != null;
    }

    private int compareStopsByPriority(BusStop stop1, BusStop stop2, Location location) {
        // Сначала приоритет по типу остановки (крупные остановки важнее)
        int priorityCompare = Boolean.compare(stop2.getIsMajorStop(), stop1.getIsMajorStop());
        if (priorityCompare != 0) return priorityCompare;

        double dist1 = location.distanceTo(
                stop1.getLatitude().doubleValue(), stop1.getLongitude().doubleValue());
        double dist2 = location.distanceTo(
                stop2.getLatitude().doubleValue(), stop2.getLongitude().doubleValue());

        return Double.compare(dist1, dist2);
    }

    protected boolean isWalkingDistanceReasonable(Location from, Location to) {
        double distance = from.distanceTo(to);
        return distance <= RoutingConstants.MAX_WALKING_DISTANCE_METERS;
    }

    protected Mono<Integer> calculateAndValidateWalkingTime(Location from, Location to) {
        return Mono.fromCallable(() -> etaCalculationService.calculateWalkingTimeMinutes(from, to))
                .filter(time -> time <= RoutingConstants.MAX_WALKING_TIME_MINUTES)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Walking time too long")));
    }

    protected boolean isRouteTimeReasonable(int travelMinutes) {
        return travelMinutes >= RoutingConstants.MIN_ROUTE_TIME_MINUTES &&
                travelMinutes <= RoutingConstants.MAX_ROUTE_TIME_MINUTES;
    }

    protected Location createLocationFromStop(BusStop stop) {
        return new Location(
                stop.getLatitude().doubleValue(),
                stop.getLongitude().doubleValue(),
                stop.getStopName()
        );
    }

    protected void publishTripPlanEvents(TripPlan tripPlan) {
        tripPlan.getUncommittedEvents().forEach(eventBus::publish);
        tripPlan.markEventsAsCommitted();
    }

    protected void logNearbyStopsFound(Location location, List<BusStop> stops) {
        log.debug("Found {} nearby stops for {}: {}",
                stops.size(),
                location.getDescription(),
                stops.stream()
                        .map(stop -> String.format("%s(%.0fm)",
                                stop.getStopName(),
                                location.distanceTo(stop.getLatitude().doubleValue(), stop.getLongitude().doubleValue())))
                        .collect(Collectors.joining(", "))
        );
    }

    protected void addWalkingOptionIfViable(TripPlan tripPlan, Location from, Location to) {
        if (!tripPlan.isWalkable()) return;

        try {
            int walkingMinutes = tripPlan.getWalkingTimeMinutes();
            List<RouteSegment> segments = List.of(
                    RouteSegment.walkingSegment(from, to, walkingMinutes)
            );
            TripOption walkingOption = new TripOption(TripType.DIRECT, segments);
            tripPlan.addTripOption(walkingOption);
            log.debug("Added walking option: {} minutes", walkingMinutes);
        } catch (Exception e) {
            log.warn("Failed to add walking option: {}", e.getMessage());
        }
    }
}