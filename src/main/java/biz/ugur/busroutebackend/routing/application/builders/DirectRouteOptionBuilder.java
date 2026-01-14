package biz.ugur.busroutebackend.routing.application.builders;

import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.factory.RouteSegmentFactory;
import biz.ugur.busroutebackend.routing.application.factory.TripOptionFactory;
import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.services.ETACalculationService;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import biz.ugur.busroutebackend.routing.infrastructure.services.RouteGeometryTrimmingService;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class DirectRouteOptionBuilder {

    private final ETACalculationService etaCalculationService;
    private final RouteGeometryTrimmingService geometryTrimmingService;
    private final WalkingTimeCalculator walkingTimeCalculator;
    private final RouteSegmentFactory routeSegmentFactory;
    private final TripOptionFactory tripOptionFactory;

    public DirectRouteOptionBuilder(ETACalculationService etaCalculationService,
                                    RouteGeometryTrimmingService geometryTrimmingService,
                                    WalkingTimeCalculator walkingTimeCalculator,
                                    RouteSegmentFactory routeSegmentFactory,
                                    TripOptionFactory tripOptionFactory) {
        this.etaCalculationService = etaCalculationService;
        this.geometryTrimmingService = geometryTrimmingService;
        this.walkingTimeCalculator = walkingTimeCalculator;
        this.routeSegmentFactory = routeSegmentFactory;
        this.tripOptionFactory = tripOptionFactory;
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
        Coordinates fromStopLocation = createCoordinatesFromStop(directRoute.fromStop());
        Coordinates toStopLocation = createCoordinatesFromStop(directRoute.toStop());

        int walkingToStop = walkingTimeCalculator.calculateWalkingTime(
                context.fromLocation(), fromStopLocation);
        int walkingFromStop = walkingTimeCalculator.calculateWalkingTime(
                toStopLocation, context.toLocation());

        if (walkingToStop > 15 || walkingFromStop > 15) {
            throw new IllegalArgumentException("Walking time too long");
        }

        String routeNumber = directRoute.route().getRouteNumber();
        String fromStopName = directRoute.fromStop().getStopName();
        String toStopName = directRoute.toStop().getStopName();
        LocalDateTime departureTime = LocalDateTime.now();

        return Mono.zip(
                etaCalculationService.calculateTravelTimeMinutes(routeNumber, fromStopName, toStopName),
                etaCalculationService.calculateWaitingTimeMinutes(routeNumber, fromStopName, departureTime)
        ).map(tuple -> {
            int busRideTime = tuple.getT1();
            int initialWaitingMinutes = tuple.getT2();

            String routeGeometry = getCorrectRouteGeometry(directRoute);
            Integer routeDistance = getCorrectRouteDistance(directRoute);

            String trimmedGeometry = trimRouteGeometry(
                    routeGeometry,
                    directRoute.fromStop(),
                    directRoute.toStop()
            );

            List<RouteSegment> segments = List.of(
                    routeSegmentFactory.createWalkingSegment(context.fromLocation(), fromStopLocation, walkingToStop),
                    createBusSegmentWithGeometry(
                            fromStopLocation,
                            toStopLocation,
                            busRideTime,
                            routeNumber,
                            trimmedGeometry,
                            routeDistance),
                    routeSegmentFactory.createWalkingSegment(toStopLocation, context.toLocation(), walkingFromStop)
            );

            log.debug("Creating direct option for route {} with waiting time {} min",
                    routeNumber, initialWaitingMinutes);

            return tripOptionFactory.createDirectOption(segments, initialWaitingMinutes, departureTime);
        });
    }

    private String getCorrectRouteGeometry(RouteCalculationService.DirectRouteResult directRoute) {
        if (directRoute.route().hasForwardGeometry() &&
                directRoute.route().getRouteGeometryForward() != null) {
            String forwardGeom = directRoute.route().getRouteGeometryForward();
            if (!forwardGeom.isEmpty()) {
                return forwardGeom;
            }
        }

        if (directRoute.route().hasBackwardGeometry() &&
                directRoute.route().getRouteGeometryBackward() != null) {
            String backwardGeom = directRoute.route().getRouteGeometryBackward();
            if (!backwardGeom.isEmpty()) {
                log.debug("✅ Using BACKWARD geometry for route {}",
                        directRoute.route().getRouteNumber());
                return backwardGeom;
            }
        }

        log.warn("⚠️ No geometry found for route {}", directRoute.route().getRouteNumber());
        return null;
    }

    private Integer getCorrectRouteDistance(RouteCalculationService.DirectRouteResult directRoute) {
        if (directRoute.route().getTotalDistanceForwardMeters() != null &&
                directRoute.route().getTotalDistanceForwardMeters() > 0) {
            return directRoute.route().getTotalDistanceForwardMeters();
        }

        if (directRoute.route().getTotalDistanceBackwardMeters() != null &&
                directRoute.route().getTotalDistanceBackwardMeters() > 0) {
            log.debug("✅ Using BACKWARD distance for route {}",
                    directRoute.route().getRouteNumber());
            return directRoute.route().getTotalDistanceBackwardMeters();
        }

        return null;
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

    private RouteSegment createBusSegmentWithGeometry(Coordinates from, Coordinates to, int durationMinutes,
                                                      String routeNumber, String geometry, Integer distance) {
        if (geometry != null) {
            return routeSegmentFactory.createBusRideSegmentWithGeometry(from, to, durationMinutes, routeNumber, geometry, distance);
        } else {
            return routeSegmentFactory.createBusRideSegment(from, to, durationMinutes, routeNumber);
        }
    }

    private Coordinates createCoordinatesFromStop(BusStop stop) {
        return Coordinates.of(
                stop.getLatitude().doubleValue(),
                stop.getLongitude().doubleValue()
        );
    }
}