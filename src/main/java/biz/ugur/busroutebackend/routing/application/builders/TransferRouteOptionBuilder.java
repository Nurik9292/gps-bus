package biz.ugur.busroutebackend.routing.application.builders;

import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.factory.RouteSegmentFactory;
import biz.ugur.busroutebackend.routing.application.factory.TripOptionFactory;
import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.services.ETACalculationService;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.routing.domain.services.WalkingRouteService;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import biz.ugur.busroutebackend.routing.infrastructure.services.RouteGeometryTrimmingService;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class TransferRouteOptionBuilder {

    private final ETACalculationService etaCalculationService;
    private final WalkingTimeCalculator walkingTimeCalculator;
    private final RouteGeometryTrimmingService geometryTrimmingService;
    private final RouteSegmentFactory routeSegmentFactory;
    private final TripOptionFactory tripOptionFactory;
    private final WalkingRouteService walkingRouteService;

    public TransferRouteOptionBuilder(ETACalculationService etaCalculationService,
                                      WalkingTimeCalculator walkingTimeCalculator,
                                      RouteGeometryTrimmingService geometryTrimmingService,
                                      RouteSegmentFactory routeSegmentFactory,
                                      TripOptionFactory tripOptionFactory,
                                      WalkingRouteService walkingRouteService) {
        this.etaCalculationService = etaCalculationService;
        this.walkingTimeCalculator = walkingTimeCalculator;
        this.geometryTrimmingService = geometryTrimmingService;
        this.routeSegmentFactory = routeSegmentFactory;
        this.tripOptionFactory = tripOptionFactory;
        this.walkingRouteService = walkingRouteService;
    }

    public Mono<TripOption> createOneTransferOption(RouteCalculationService.TransferRouteResult transferRoute,
                                                    SearchContext context) {
        return Mono.defer(() -> buildOneTransferOption(transferRoute, context))
                .onErrorResume(error -> {
                    log.debug("Failed to create one-transfer option: {}", error.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<TripOption> createTwoTransferOption(RouteCalculationService.TwoTransferRouteResult twoTransferRoute,
                                                    SearchContext context) {
        return Mono.defer(() -> buildTwoTransferOption(twoTransferRoute, context))
                .onErrorResume(error -> {
                    log.debug("Failed to create two-transfer option: {}", error.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<TripOption> buildOneTransferOption(RouteCalculationService.TransferRouteResult transferRoute,
                                                    SearchContext context) {

        Coordinates firstStopLocation = createCoordinatesFromStop(transferRoute.fromStop());
        Coordinates transferStopLocation = createCoordinatesFromStop(transferRoute.transferStop());
        Coordinates lastStopLocation = createCoordinatesFromStop(transferRoute.toStop());

        int walkingToFirst = walkingTimeCalculator.calculateWalkingTime(
                context.fromLocation(), firstStopLocation);
        int walkingFromLast = walkingTimeCalculator.calculateWalkingTime(
                lastStopLocation, context.toLocation());

        if (walkingToFirst > 15 || walkingFromLast > 15) {
            return Mono.error(new IllegalArgumentException("Walking time too long"));
        }

        String firstRouteNumber = transferRoute.firstRoute().getRouteNumber();
        String fromStopName = transferRoute.fromStop().getStopName();
        LocalDateTime departureTime = LocalDateTime.now();

        return Mono.zip(
                etaCalculationService.calculateWaitingTimeMinutes(firstRouteNumber, fromStopName, departureTime),
                walkingRouteService.getWalkingRoute(context.fromLocation(), firstStopLocation),
                walkingRouteService.getWalkingRoute(lastStopLocation, context.toLocation())
        ).map(tuple -> {
            int initialWaitingMinutes = tuple.getT1();
            WalkingRouteService.WalkingRouteResult walkToFirst = tuple.getT2();
            WalkingRouteService.WalkingRouteResult walkFromLast = tuple.getT3();

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

            String transferStopName = transferRoute.transferStop().getStopName();
            String lastStopName = transferRoute.toStop().getStopName();
            String secondRouteNumber = transferRoute.secondRoute().getRouteNumber();

            RouteSegment walkToSeg = routeSegmentFactory.createWalkingSegment(context.fromLocation(), firstStopLocation, walkingToFirst, walkToFirst);
            walkToSeg.setToLocationName(fromStopName);

            RouteSegment firstBusSeg = createBusSegmentWithGeometry(firstStopLocation, transferStopLocation, transferRoute.firstRouteTravelMinutes(), firstRouteNumber, firstRouteTrimmed, firstRouteDistance);
            firstBusSeg.setFromLocationName(fromStopName);
            firstBusSeg.setToLocationName(transferStopName);

            RouteSegment transferSeg = routeSegmentFactory.createTransferSegment(transferStopLocation, transferRoute.transferWaitMinutes());
            transferSeg.setFromLocationName(transferStopName);
            transferSeg.setToLocationName(transferStopName);

            RouteSegment secondBusSeg = createBusSegmentWithGeometry(transferStopLocation, lastStopLocation, transferRoute.secondRouteTravelMinutes(), secondRouteNumber, secondRouteTrimmed, secondRouteDistance);
            secondBusSeg.setFromLocationName(transferStopName);
            secondBusSeg.setToLocationName(lastStopName);

            RouteSegment walkFromSeg = routeSegmentFactory.createWalkingSegment(lastStopLocation, context.toLocation(), walkingFromLast, walkFromLast);
            walkFromSeg.setFromLocationName(lastStopName);

            List<RouteSegment> segments = List.of(walkToSeg, firstBusSeg, transferSeg, secondBusSeg, walkFromSeg);

            log.debug("Creating one-transfer option for routes {}->{} with waiting time {} min",
                    firstRouteNumber, transferRoute.secondRoute().getRouteNumber(), initialWaitingMinutes);

            return tripOptionFactory.createOneTransferOption(segments, initialWaitingMinutes, departureTime);
        });
    }

    private Mono<TripOption> buildTwoTransferOption(RouteCalculationService.TwoTransferRouteResult twoTransferRoute,
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
            return Mono.error(new IllegalArgumentException("Walking time too long for two transfers"));
        }

        String firstRouteNumber = twoTransferRoute.firstRoute().getRouteNumber();
        String fromStopName = twoTransferRoute.fromStop().getStopName();
        LocalDateTime departureTime = LocalDateTime.now();

        return Mono.zip(
                etaCalculationService.calculateWaitingTimeMinutes(firstRouteNumber, fromStopName, departureTime),
                walkingRouteService.getWalkingRoute(context.fromLocation(), firstStopLocation),
                walkingRouteService.getWalkingRoute(finalStopLocation, context.toLocation())
        ).map(tuple -> {
            int initialWaitingMinutes = tuple.getT1();
            WalkingRouteService.WalkingRouteResult walkToFirst = tuple.getT2();
            WalkingRouteService.WalkingRouteResult walkFromFinal = tuple.getT3();

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

            String firstTransferStopName = twoTransferRoute.firstTransferStop().getStopName();
            String secondTransferStopName = twoTransferRoute.secondTransferStop().getStopName();
            String finalStopName = twoTransferRoute.toStop().getStopName();
            String secondRouteNum = twoTransferRoute.secondRoute().getRouteNumber();
            String thirdRouteNum = twoTransferRoute.thirdRoute().getRouteNumber();

            RouteSegment walkToSeg2 = routeSegmentFactory.createWalkingSegment(context.fromLocation(), firstStopLocation, walkingToFirst, walkToFirst);
            walkToSeg2.setToLocationName(fromStopName);

            RouteSegment firstBusSeg2 = createBusSegmentWithGeometry(firstStopLocation, firstTransferLocation, twoTransferRoute.firstRouteTravelMinutes(), firstRouteNumber, firstRouteTrimmed, getCorrectRouteDistance(twoTransferRoute.firstRoute()));
            firstBusSeg2.setFromLocationName(fromStopName);
            firstBusSeg2.setToLocationName(firstTransferStopName);

            RouteSegment transfer1Seg = routeSegmentFactory.createTransferSegment(firstTransferLocation, twoTransferRoute.firstTransferWaitMinutes());
            transfer1Seg.setFromLocationName(firstTransferStopName);
            transfer1Seg.setToLocationName(firstTransferStopName);

            RouteSegment secondBusSeg2 = createBusSegmentWithGeometry(firstTransferLocation, secondTransferLocation, twoTransferRoute.secondRouteTravelMinutes(), secondRouteNum, secondRouteTrimmed, getCorrectRouteDistance(twoTransferRoute.secondRoute()));
            secondBusSeg2.setFromLocationName(firstTransferStopName);
            secondBusSeg2.setToLocationName(secondTransferStopName);

            RouteSegment transfer2Seg = routeSegmentFactory.createTransferSegment(secondTransferLocation, twoTransferRoute.secondTransferWaitMinutes());
            transfer2Seg.setFromLocationName(secondTransferStopName);
            transfer2Seg.setToLocationName(secondTransferStopName);

            RouteSegment thirdBusSeg2 = createBusSegmentWithGeometry(secondTransferLocation, finalStopLocation, twoTransferRoute.thirdRouteTravelMinutes(), thirdRouteNum, thirdRouteTrimmed, getCorrectRouteDistance(twoTransferRoute.thirdRoute()));
            thirdBusSeg2.setFromLocationName(secondTransferStopName);
            thirdBusSeg2.setToLocationName(finalStopName);

            RouteSegment walkFromSeg2 = routeSegmentFactory.createWalkingSegment(finalStopLocation, context.toLocation(), walkingFromFinal, walkFromFinal);
            walkFromSeg2.setFromLocationName(finalStopName);

            List<RouteSegment> segments = List.of(walkToSeg2, firstBusSeg2, transfer1Seg, secondBusSeg2, transfer2Seg, thirdBusSeg2, walkFromSeg2);

            log.debug("Creating two-transfer option for routes {}->{}->{}  with waiting time {} min",
                    firstRouteNumber, twoTransferRoute.secondRoute().getRouteNumber(),
                    twoTransferRoute.thirdRoute().getRouteNumber(), initialWaitingMinutes);

            return tripOptionFactory.createTwoTransferOption(segments, initialWaitingMinutes, departureTime);
        });
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
