package biz.ugur.busroutebackend.routing.application.builders;

import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.services.ETACalculationService;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.routing.domain.volumeojects.Location;
import biz.ugur.busroutebackend.routing.domain.volumeojects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.volumeojects.TripOption;
import biz.ugur.busroutebackend.routing.infrastructure.services.RouteGeometryTrimmingService;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Slf4j
public class DirectRouteOptionBuilder {

    private final ETACalculationService etaCalculationService;
    private final RouteGeometryTrimmingService geometryTrimmingService;
    private final WalkingTimeCalculator walkingTimeCalculator;

    public DirectRouteOptionBuilder(ETACalculationService etaCalculationService,
                                    RouteGeometryTrimmingService geometryTrimmingService,
                                    WalkingTimeCalculator walkingTimeCalculator) {
        this.etaCalculationService = etaCalculationService;
        this.geometryTrimmingService = geometryTrimmingService;
        this.walkingTimeCalculator = walkingTimeCalculator;
    }

    public Mono<TripOption> createOption(RouteCalculationService.DirectRouteResult directRoute,
                                         SearchContext context) {
        return buildDirectOption(directRoute, context)
                .onErrorResume(error -> {
                    log.debug("Failed to create direct option for route {}: {}",
                            directRoute.route().getRouteNumber(), error.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<TripOption> buildDirectOption(RouteCalculationService.DirectRouteResult directRoute,
                                         SearchContext context) {
        Location fromStopLocation = createLocationFromStop(directRoute.fromStop());
        Location toStopLocation = createLocationFromStop(directRoute.toStop());

        int walkingToStop = walkingTimeCalculator.calculateWalkingTime(
                context.fromLocation(), fromStopLocation);
        int walkingFromStop = walkingTimeCalculator.calculateWalkingTime(
                toStopLocation, context.toLocation());

        if (walkingToStop > 15 || walkingFromStop > 15) {
            throw new IllegalArgumentException("Walking time too long");
        }


        return etaCalculationService.calculateTravelTimeMinutes(
                directRoute.route().getRouteNumber(),
                directRoute.fromStop().getStopName(),
                directRoute.toStop().getStopName()
        ).map(busRideTime -> {

            String trimmedGeometry = trimRouteGeometry(
                    directRoute.route().getRouteGeometryForward(),
                    directRoute.fromStop(),
                    directRoute.toStop()
            );

            List<RouteSegment> segments = List.of(
                    RouteSegment.walkingSegment(context.fromLocation(), fromStopLocation, walkingToStop),
                    createBusSegmentWithGeometry(fromStopLocation, toStopLocation, busRideTime,
                            directRoute.route().getRouteNumber(), trimmedGeometry,
                            directRoute.route().getTotalDistanceForwardMeters()),
                    RouteSegment.walkingSegment(toStopLocation, context.toLocation(), walkingFromStop)
            );

            return new TripOption(TripType.DIRECT, segments);

        });

//        int busRideTime = etaCalculationService.calculateTravelTimeMinutes(
//                directRoute.route().getRouteNumber(),
//                directRoute.fromStop().getStopName(),
//                directRoute.toStop().getStopName()
//        );


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

    private Location createLocationFromStop(BusStop stop) {
        return new Location(
                stop.getLatitude().doubleValue(),
                stop.getLongitude().doubleValue(),
                stop.getStopName()
        );
    }
}