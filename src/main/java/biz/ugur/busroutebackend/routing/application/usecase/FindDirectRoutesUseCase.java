package biz.ugur.busroutebackend.routing.application.usecase;

import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import biz.ugur.busroutebackend.routing.domain.services.ETACalculationService;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.routing.domain.volumeojects.*;
import biz.ugur.busroutebackend.routing.infrastructure.services.RouteGeometryTrimmingService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;


@Service
@Slf4j
public class FindDirectRoutesUseCase extends BaseRouteUseCase implements UseCase<FindDirectRoutesUseCase.Command, Mono<TripPlan>> {

    private final RouteGeometryTrimmingService geometryTrimmingService;

    public FindDirectRoutesUseCase(RouteCalculationService routeCalculationService,
                                   ETACalculationService etaCalculationService,
                                   EventBus eventBus,
                                   RouteGeometryTrimmingService geometryTrimmingService) {
        super(routeCalculationService, etaCalculationService, eventBus);
        this.geometryTrimmingService = geometryTrimmingService;
    }

    @Override
    public Mono<TripPlan> execute(Command command) {
        log.info("Finding direct routes from {} to {}",
                command.fromLocation.getDescription(), command.toLocation.getDescription());

        TripSearchCriteria criteria = command.searchCriteria != null ?
                command.searchCriteria : TripSearchCriteria.defaultCriteria();

        return Mono.fromCallable(() -> new TripPlan(TripPlanId.generate(), command.fromLocation, command.toLocation, criteria))
                .flatMap(tripPlan -> {
                    addWalkingOptionIfViable(tripPlan, command.fromLocation, command.toLocation);

                    return findBusRoutes(command.fromLocation, command.toLocation, criteria, tripPlan);
                })
                .doOnSuccess(this::logSearchResults)
                .doOnError(error -> log.error("Error finding direct routes", error));
    }

    private Mono<TripPlan> findBusRoutes(Location fromLocation, Location toLocation,
                                         TripSearchCriteria criteria, TripPlan tripPlan) {
        return Mono.zip(
                findNearbyStopsWithLimit(fromLocation, RoutingConstants.DEFAULT_NEARBY_RADIUS_KM, RoutingConstants.DEFAULT_MAX_STOPS),
                findNearbyStopsWithLimit(toLocation, RoutingConstants.DEFAULT_NEARBY_RADIUS_KM, RoutingConstants.DEFAULT_MAX_STOPS)
        ).flatMap(tuple -> {
            List<BusStop> fromStops = tuple.getT1();
            List<BusStop> toStops = tuple.getT2();

            if (fromStops.isEmpty() || toStops.isEmpty()) {
                log.warn("No bus stops found near locations");
                return Mono.just(tripPlan);
            }

            return routeCalculationService.findDirectRoutes(fromStops, toStops)
                    .filter(route -> isRouteTimeReasonable(route.estimatedTravelMinutes()))
                    .flatMap(route -> createDirectTripOption(route, fromLocation, toLocation))
                    .filter(Objects::nonNull)
                    .take(10)
                    .collectList()
                    .map(tripOptions -> {
                        tripOptions.forEach(tripPlan::addTripOption);
                        publishTripPlanEvents(tripPlan);
                        return tripPlan;
                    });
        });
    }

    private Mono<TripOption> createDirectTripOption(RouteCalculationService.DirectRouteResult directRoute,
                                                    Location originalFrom, Location originalTo) {
        Location fromStopLocation = createLocationFromStop(directRoute.fromStop());
        Location toStopLocation = createLocationFromStop(directRoute.toStop());

        return Mono.zip(
                calculateAndValidateWalkingTime(originalFrom, fromStopLocation),
                etaCalculationService.calculateTravelTimeMinutes(
                        directRoute.route().getRouteNumber(),
                        directRoute.fromStop().getStopName(),
                        directRoute.toStop().getStopName()
                ),
                calculateAndValidateWalkingTime(toStopLocation, originalTo)
        ).map(tuple -> {
            int walkingToStop = tuple.getT1();
            int busRideTime = tuple.getT2();
            int walkingFromStop = tuple.getT3();

            String trimmedGeometry = trimRouteGeometry(
                    directRoute.route().getRouteGeometryForward(),
                    directRoute.fromStop(),
                    directRoute.toStop()
            );

            List<RouteSegment> segments = List.of(
                    RouteSegment.walkingSegment(originalFrom, fromStopLocation, walkingToStop),
                    createBusSegmentWithGeometry(
                            fromStopLocation, toStopLocation, busRideTime,
                            directRoute.route().getRouteNumber(), trimmedGeometry,
                            directRoute.route().getTotalDistanceForwardMeters()
                    ),
                    RouteSegment.walkingSegment(toStopLocation, originalTo, walkingFromStop)
            );

            return new TripOption(TripType.DIRECT, segments);
        }).onErrorResume(error -> {
            log.debug("Failed to create trip option for route {}: {}",
                    directRoute.route().getRouteNumber(), error.getMessage());
            return Mono.empty();
        });
    }

    private String trimRouteGeometry(String originalGeometry, BusStop fromStop, BusStop toStop) {
        if (originalGeometry == null || !geometryTrimmingService.isValidGeometry(originalGeometry)) {
            return originalGeometry;
        }

        try {
            String trimmed = geometryTrimmingService.trimRouteGeometry(originalGeometry, fromStop, toStop);
            return trimmed != null ? trimmed : originalGeometry;
        } catch (Exception e) {
            log.warn("Failed to trim geometry: {}", e.getMessage());
            return originalGeometry;
        }
    }

    private RouteSegment createBusSegmentWithGeometry(Location from, Location to, int durationMinutes,
                                                      String routeNumber, String geometry, Integer distance) {
        if (geometry != null) {
            return RouteSegment.busRideSegmentWithGeometry(from, to, durationMinutes, routeNumber, geometry, distance);
        } else {
            return RouteSegment.busRideSegment(from, to, durationMinutes, routeNumber);
        }
    }

    private void logSearchResults(TripPlan plan) {
        int optionsCount = plan.getTripOptions().size();
        if (optionsCount > 0) {
            TripOption fastest = plan.getFastestOption();
            log.info("Found {} direct route options. Fastest: {} minutes",
                    optionsCount, fastest != null ? fastest.getTotalTravelMinutes() : "N/A");
        } else {
            log.info("No direct routes found between the locations");
        }
    }

    public record Command(Location fromLocation, Location toLocation, TripSearchCriteria searchCriteria) {
        public Command(Location fromLocation, Location toLocation) {
            this(fromLocation, toLocation, TripSearchCriteria.defaultCriteria());
        }
    }
}