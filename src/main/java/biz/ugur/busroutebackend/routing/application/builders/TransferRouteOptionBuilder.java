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
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Slf4j
public class TransferRouteOptionBuilder {

    private final ETACalculationService etaCalculationService;
    private final WalkingTimeCalculator walkingTimeCalculator;
    private final RouteGeometryTrimmingService geometryTrimmingService;
    private final RouteSegmentFactory routeSegmentFactory;
    private final TripOptionFactory tripOptionFactory;

    public TransferRouteOptionBuilder(ETACalculationService etaCalculationService,
                                      WalkingTimeCalculator walkingTimeCalculator,
                                      RouteGeometryTrimmingService geometryTrimmingService,
                                      RouteSegmentFactory routeSegmentFactory,
                                      TripOptionFactory tripOptionFactory) {
        this.etaCalculationService = etaCalculationService;
        this.walkingTimeCalculator = walkingTimeCalculator;
        this.geometryTrimmingService = geometryTrimmingService;
        this.routeSegmentFactory = routeSegmentFactory;
        this.tripOptionFactory = tripOptionFactory;
    }

    public Mono<TripOption> createOneTransferOption(RouteCalculationService.TransferRouteResult transferRoute,
                                                    SearchContext context) {
        return Mono.fromCallable(() -> buildOneTransferOption(transferRoute, context))
                .onErrorResume(error -> {
                    log.debug("Failed to create one-transfer option: {}", error.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<TripOption> createTwoTransferOption(RouteCalculationService.TwoTransferRouteResult twoTransferRoute,
                                                    SearchContext context) {
        return Mono.fromCallable(() -> buildTwoTransferOption(twoTransferRoute, context))
                .onErrorResume(error -> {
                    log.debug("Failed to create two-transfer option: {}", error.getMessage());
                    return Mono.empty();
                });
    }

    private TripOption buildOneTransferOption(RouteCalculationService.TransferRouteResult transferRoute,
                                              SearchContext context) {

        Coordinates firstStopLocation = createCoordinatesFromStop(transferRoute.fromStop());
        Coordinates transferStopLocation = createCoordinatesFromStop(transferRoute.transferStop());
        Coordinates lastStopLocation = createCoordinatesFromStop(transferRoute.toStop());

        int walkingToFirst = walkingTimeCalculator.calculateWalkingTime(
                context.fromLocation(), firstStopLocation);
        int walkingFromLast = walkingTimeCalculator.calculateWalkingTime(
                lastStopLocation, context.toLocation());

        if (walkingToFirst > 15 || walkingFromLast > 15) {
            throw new IllegalArgumentException("Walking time too long");
        }

        String firstRouteGeometry = getCorrectRouteGeometry(transferRoute.firstRoute());
        Integer firstRouteDistance = getCorrectRouteDistance(transferRoute.firstRoute());

        String secondRouteGeometry = getCorrectRouteGeometry(transferRoute.secondRoute());
        Integer secondRouteDistance = getCorrectRouteDistance(transferRoute.secondRoute());

        String firstRouteTrimmed = trimRouteGeometry(
                firstRouteGeometry,
                transferRoute.fromStop(),
                transferRoute.transferStop()
        );

        String secondRouteTrimmed = trimRouteGeometry(
                secondRouteGeometry,
                transferRoute.transferStop(),
                transferRoute.toStop()
        );

        List<RouteSegment> segments = List.of(
                routeSegmentFactory.createWalkingSegment(context.fromLocation(), firstStopLocation, walkingToFirst),
                createBusSegmentWithGeometry(
                        firstStopLocation,
                        transferStopLocation,
                        transferRoute.firstRouteTravelMinutes(),
                        transferRoute.firstRoute().getRouteNumber(),
                        firstRouteTrimmed,
                        firstRouteDistance),
                routeSegmentFactory.createTransferSegment(transferStopLocation, transferRoute.transferWaitMinutes()),
                createBusSegmentWithGeometry(
                        transferStopLocation,
                        lastStopLocation,
                        transferRoute.secondRouteTravelMinutes(),
                        transferRoute.secondRoute().getRouteNumber(),
                        secondRouteTrimmed,
                        secondRouteDistance),
                routeSegmentFactory.createWalkingSegment(lastStopLocation, context.toLocation(), walkingFromLast)
        );

        return tripOptionFactory.createOneTransferOption(segments);
    }

    private TripOption buildTwoTransferOption(RouteCalculationService.TwoTransferRouteResult twoTransferRoute,
                                              SearchContext context) {

        Coordinates firstStopLocation = createCoordinatesFromStop(twoTransferRoute.fromStop());
        Coordinates firstTransferLocation = createCoordinatesFromStop(twoTransferRoute.firstTransferStop());
        Coordinates secondTransferLocation = createCoordinatesFromStop(twoTransferRoute.secondTransferStop());
        Coordinates finalStopLocation = createCoordinatesFromStop(twoTransferRoute.toStop());

        int walkingToFirst = walkingTimeCalculator.calculateWalkingTime(
                context.fromLocation(), firstStopLocation);
        int walkingFromFinal = walkingTimeCalculator.calculateWalkingTime(
                finalStopLocation, context.toLocation());

        if (walkingToFirst > 18 || walkingFromFinal > 18) {
            throw new IllegalArgumentException("Walking time too long for two transfers");
        }

        String firstRouteGeometry = getCorrectRouteGeometry(twoTransferRoute.firstRoute());
        String secondRouteGeometry = getCorrectRouteGeometry(twoTransferRoute.secondRoute());
        String thirdRouteGeometry = getCorrectRouteGeometry(twoTransferRoute.thirdRoute());

        String firstRouteTrimmed = trimRouteGeometry(
                firstRouteGeometry,
                twoTransferRoute.fromStop(),
                twoTransferRoute.firstTransferStop()
        );

        String secondRouteTrimmed = trimRouteGeometry(
                secondRouteGeometry,
                twoTransferRoute.firstTransferStop(),
                twoTransferRoute.secondTransferStop()
        );

        String thirdRouteTrimmed = trimRouteGeometry(
                thirdRouteGeometry,
                twoTransferRoute.secondTransferStop(),
                twoTransferRoute.toStop()
        );

        List<RouteSegment> segments = List.of(
                routeSegmentFactory.createWalkingSegment(context.fromLocation(), firstStopLocation, walkingToFirst),
                createBusSegmentWithGeometry(
                        firstStopLocation,
                        firstTransferLocation,
                        twoTransferRoute.firstRouteTravelMinutes(),
                        twoTransferRoute.firstRoute().getRouteNumber(),
                        firstRouteTrimmed,
                        getCorrectRouteDistance(twoTransferRoute.firstRoute())),
                routeSegmentFactory.createTransferSegment(firstTransferLocation, twoTransferRoute.firstTransferWaitMinutes()),
                createBusSegmentWithGeometry(
                        firstTransferLocation,
                        secondTransferLocation,
                        twoTransferRoute.secondRouteTravelMinutes(),
                        twoTransferRoute.secondRoute().getRouteNumber(),
                        secondRouteTrimmed,
                        getCorrectRouteDistance(twoTransferRoute.secondRoute())),
                routeSegmentFactory.createTransferSegment(secondTransferLocation, twoTransferRoute.secondTransferWaitMinutes()),
                createBusSegmentWithGeometry(
                        secondTransferLocation,
                        finalStopLocation,
                        twoTransferRoute.thirdRouteTravelMinutes(),
                        twoTransferRoute.thirdRoute().getRouteNumber(),
                        thirdRouteTrimmed,
                        getCorrectRouteDistance(twoTransferRoute.thirdRoute())),
                routeSegmentFactory.createWalkingSegment(finalStopLocation, context.toLocation(), walkingFromFinal)
        );

        return tripOptionFactory.createTwoTransferOption(segments);
    }

    private Coordinates createCoordinatesFromStop(BusStop stop) {
        return Coordinates.of(
                stop.getLatitude().doubleValue(),
                stop.getLongitude().doubleValue()
        );
    }

    private String getCorrectRouteGeometry(BusRoute route) {
        if (route.hasForwardGeometry() && route.getRouteGeometryForward() != null) {
            String forwardGeom = route.getRouteGeometryForward();
            if (!forwardGeom.isEmpty()) {
                return forwardGeom;
            }
        }

        if (route.hasBackwardGeometry() && route.getRouteGeometryBackward() != null) {
            String backwardGeom = route.getRouteGeometryBackward();
            if (!backwardGeom.isEmpty()) {
                log.debug("✅ Using BACKWARD geometry for route {}", route.getRouteNumber());
                return backwardGeom;
            }
        }

        log.warn("⚠️ No geometry found for route {}", route.getRouteNumber());
        return null;
    }

    private Integer getCorrectRouteDistance(BusRoute route) {
        if (route.getTotalDistanceForwardMeters() != null &&
                route.getTotalDistanceForwardMeters() > 0) {
            return route.getTotalDistanceForwardMeters();
        }

        if (route.getTotalDistanceBackwardMeters() != null &&
                route.getTotalDistanceBackwardMeters() > 0) {
            log.debug("✅ Using BACKWARD distance for route {}", route.getRouteNumber());
            return route.getTotalDistanceBackwardMeters();
        }

        return null;
    }

    private String determineDirection(BusRoute route) {
        if (route.getRouteGeometryForward() != null && !route.getRouteGeometryForward().isEmpty()) {
            return "FORWARD";
        }
        if (route.getRouteGeometryBackward() != null && !route.getRouteGeometryBackward().isEmpty()) {
            return "BACKWARD";
        }
        return "UNKNOWN";
    }

    private String trimRouteGeometry(String originalGeometry, BusStop fromStop, BusStop toStop) {
        if (originalGeometry == null || !geometryTrimmingService.isValidGeometry(originalGeometry)) {
            return originalGeometry;
        }

        try {
            String trimmed = geometryTrimmingService.trimRouteGeometry(originalGeometry, fromStop, toStop);
            return trimmed != null ? trimmed : originalGeometry;
        } catch (Exception e) {
            log.warn("Failed to trim geometry for transfer route: {}", e.getMessage());
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
}
