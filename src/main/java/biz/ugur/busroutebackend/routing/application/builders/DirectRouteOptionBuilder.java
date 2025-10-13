package biz.ugur.busroutebackend.routing.application.builders;

import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
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
        Coordinates fromStopLocation = createCoordinatesFromStop(directRoute.fromStop());
        Coordinates toStopLocation = createCoordinatesFromStop(directRoute.toStop());

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

            String routeGeometry = getCorrectRouteGeometry(directRoute);
            Integer routeDistance = getCorrectRouteDistance(directRoute);


            String trimmedGeometry = trimRouteGeometry(
                    routeGeometry,
                    directRoute.fromStop(),
                    directRoute.toStop()
            );

            List<RouteSegment> segments = List.of(
                    RouteSegment.walkingSegment(context.fromLocation(), fromStopLocation, walkingToStop),
                    createBusSegmentWithGeometry(
                            fromStopLocation,
                            toStopLocation,
                            busRideTime,
                            directRoute.route().getRouteNumber(),
                            trimmedGeometry,
                            routeDistance),
                    RouteSegment.walkingSegment(toStopLocation, context.toLocation(), walkingFromStop)
            );

            return new TripOption(TripType.DIRECT, segments);

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
            return RouteSegment.busRideSegmentWithGeometry(from, to, durationMinutes, routeNumber, geometry, distance);
        } else {
            return RouteSegment.busRideSegment(from, to, durationMinutes, routeNumber);
        }
    }

    private Coordinates createCoordinatesFromStop(BusStop stop) {
        return Coordinates.of(
                stop.getLatitude().doubleValue(),
                stop.getLongitude().doubleValue()
        );
    }
}