package biz.ugur.busroutebackend.routing.application.builders;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.factory.RouteSegmentFactory;
import biz.ugur.busroutebackend.routing.application.factory.TripOptionFactory;
import biz.ugur.busroutebackend.routing.domain.services.ETACalculationService;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.routing.domain.services.WalkingRouteService;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import biz.ugur.busroutebackend.routing.infrastructure.services.RouteGeometrySelector;
import biz.ugur.busroutebackend.routing.infrastructure.services.RouteGeometryTrimmingService;
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
    private final WalkingRouteService walkingRouteService;

    public DirectRouteOptionBuilder(ETACalculationService etaCalculationService,
                                    RouteGeometryTrimmingService geometryTrimmingService,
                                    WalkingTimeCalculator walkingTimeCalculator,
                                    RouteSegmentFactory routeSegmentFactory,
                                    TripOptionFactory tripOptionFactory,
                                    WalkingRouteService walkingRouteService) {
        this.etaCalculationService = etaCalculationService;
        this.geometryTrimmingService = geometryTrimmingService;
        this.walkingTimeCalculator = walkingTimeCalculator;
        this.routeSegmentFactory = routeSegmentFactory;
        this.tripOptionFactory = tripOptionFactory;
        this.walkingRouteService = walkingRouteService;
    }

    public Mono<TripOption> createOption(RouteCalculationService.DirectRouteResult directRoute,
                                         SearchContext context) {
        log.info("[{}] Creating option for route {}", context.searchId(), directRoute.route().getRouteNumber());
        return Mono.defer(() -> buildDirectOption(directRoute, context))
                .onErrorResume(error -> {
                    log.warn("[{}] Failed to create direct option for route {}: {}",
                            context.searchId(), directRoute.route().getRouteNumber(), error.getMessage());
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
        long startTime = System.currentTimeMillis();

        Mono<Integer> travelTime = etaCalculationService.calculateTravelTimeMinutes(routeNumber, fromStopName, toStopName)
                .doOnSuccess(t -> log.info("[{}] ETA travelTime completed for route {} in {}ms",
                        context.searchId(), routeNumber, System.currentTimeMillis() - startTime));

        Mono<Integer> waitingTime = etaCalculationService.calculateWaitingTimeMinutes(routeNumber, fromStopName, departureTime)
                .doOnSuccess(t -> log.info("[{}] ETA waitingTime completed for route {} in {}ms",
                        context.searchId(), routeNumber, System.currentTimeMillis() - startTime));

        Mono<WalkingRouteService.WalkingRouteResult> walkTo = walkingRouteService.getWalkingRoute(context.fromLocation(), fromStopLocation)
                .doOnSuccess(t -> log.info("[{}] OSRM walkTo completed for route {} in {}ms",
                        context.searchId(), routeNumber, System.currentTimeMillis() - startTime));

        Mono<WalkingRouteService.WalkingRouteResult> walkFrom = walkingRouteService.getWalkingRoute(toStopLocation, context.toLocation())
                .doOnSuccess(t -> log.info("[{}] OSRM walkFrom completed for route {} in {}ms",
                        context.searchId(), routeNumber, System.currentTimeMillis() - startTime));

        return Mono.zip(travelTime, waitingTime, walkTo, walkFrom)
                .doOnSuccess(t -> log.info("[{}] All async calls completed for route {} in {}ms",
                        context.searchId(), routeNumber, System.currentTimeMillis() - startTime))
                .map(tuple -> {
            int busRideTime = tuple.getT1();
            int initialWaitingMinutes = tuple.getT2();
            WalkingRouteService.WalkingRouteResult walkToStop = tuple.getT3();
            WalkingRouteService.WalkingRouteResult walkFromStop = tuple.getT4();

            String routeGeometry = getCorrectRouteGeometry(directRoute);

            String trimmedGeometry = trimRouteGeometry(
                    routeGeometry,
                    directRoute.fromStop(),
                    directRoute.toStop()
            );

            int calculatedDistance = geometryTrimmingService.calculateGeometryDistanceMeters(trimmedGeometry);
            Integer routeDistance = calculatedDistance > 0 ? calculatedDistance : null;

            RouteSegment walkToSeg = routeSegmentFactory.createWalkingSegment(context.fromLocation(), fromStopLocation, walkingToStop, walkToStop);
            walkToSeg.setToLocationName(fromStopName);

            RouteSegment busSeg = createBusSegmentWithGeometry(fromStopLocation, toStopLocation, busRideTime, routeNumber, trimmedGeometry, routeDistance);
            busSeg.setFromLocationName(fromStopName);
            busSeg.setToLocationName(toStopName);
            busSeg.setFromStopId(directRoute.fromStop().getId().toString());
            busSeg.setToStopId(directRoute.toStop().getId().toString());

            RouteSegment walkFromSeg = routeSegmentFactory.createWalkingSegment(toStopLocation, context.toLocation(), walkingFromStop, walkFromStop);
            walkFromSeg.setFromLocationName(toStopName);

            List<RouteSegment> segments = List.of(walkToSeg, busSeg, walkFromSeg);

            return tripOptionFactory.createDirectOption(segments, initialWaitingMinutes, departureTime);
        });
    }

    private String getCorrectRouteGeometry(RouteCalculationService.DirectRouteResult directRoute) {
        String forwardGeom = directRoute.route().hasForwardGeometry()
                ? directRoute.route().getRouteGeometryForward() : null;
        String backwardGeom = directRoute.route().hasBackwardGeometry()
                ? directRoute.route().getRouteGeometryBackward() : null;
        String chosen = RouteGeometrySelector.select(forwardGeom, backwardGeom,
                directRoute.direction(), directRoute.fromStop(), directRoute.toStop(),
                geometryTrimmingService);
        if (chosen == null) {
            log.warn("⚠️ No geometry found for route {}", directRoute.route().getRouteNumber());
        }
        return chosen;
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