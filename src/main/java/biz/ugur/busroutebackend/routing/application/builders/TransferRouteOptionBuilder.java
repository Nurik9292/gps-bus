package biz.ugur.busroutebackend.routing.application.builders;

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
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final TransferSegmentBuilder transferSegmentBuilder;

    public TransferRouteOptionBuilder(ETACalculationService etaCalculationService,
                                      WalkingTimeCalculator walkingTimeCalculator,
                                      RouteGeometryTrimmingService geometryTrimmingService,
                                      RouteSegmentFactory routeSegmentFactory,
                                      TripOptionFactory tripOptionFactory,
                                      WalkingRouteService walkingRouteService,
                                      TransferSegmentBuilder transferSegmentBuilder) {
        this.etaCalculationService = etaCalculationService;
        this.walkingTimeCalculator = walkingTimeCalculator;
        this.geometryTrimmingService = geometryTrimmingService;
        this.routeSegmentFactory = routeSegmentFactory;
        this.tripOptionFactory = tripOptionFactory;
        this.walkingRouteService = walkingRouteService;
        this.transferSegmentBuilder = transferSegmentBuilder;
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
        Coordinates secondBoardStopLocation = createCoordinatesFromStop(transferRoute.secondBoardStop());
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

        Mono<WalkingRouteService.WalkingRouteResult> transferWalk =
                TransferSegmentBuilder.isSameStop(transferStopLocation, secondBoardStopLocation)
                        ? Mono.just(WalkingRouteService.WalkingRouteResult.EMPTY)
                        : walkingRouteService.getWalkingRoute(transferStopLocation, secondBoardStopLocation);

        return Mono.zip(
                etaCalculationService.calculateWaitingTimeMinutes(firstRouteNumber, fromStopName, departureTime),
                walkingRouteService.getWalkingRoute(context.fromLocation(), firstStopLocation),
                walkingRouteService.getWalkingRoute(lastStopLocation, context.toLocation()),
                transferWalk
        ).map(tuple -> {
            int initialWaitingMinutes = tuple.getT1();
            WalkingRouteService.WalkingRouteResult walkToFirst = tuple.getT2();
            WalkingRouteService.WalkingRouteResult walkFromLast = tuple.getT3();
            WalkingRouteService.WalkingRouteResult transferWalkResult = tuple.getT4();

            String firstRouteGeometry = selectGeometryForDirection(
                    transferRoute.firstRoute(),
                    transferRoute.firstDirection(),
                    transferRoute.fromStop(), transferRoute.transferStop());
            String secondRouteGeometry = selectGeometryForDirection(
                    transferRoute.secondRoute(),
                    transferRoute.secondDirection(),
                    transferRoute.secondBoardStop(), transferRoute.toStop());

            String firstRouteTrimmed = trimRouteGeometry(
                    firstRouteGeometry,
                    transferRoute.fromStop(),
                    transferRoute.transferStop()
            );

            String secondRouteTrimmed = trimRouteGeometry(
                    secondRouteGeometry,
                    transferRoute.secondBoardStop(),
                    transferRoute.toStop()
            );

            int firstCalcDist = geometryTrimmingService.calculateGeometryDistanceMeters(firstRouteTrimmed);
            Integer firstRouteDistance = firstCalcDist > 0 ? firstCalcDist : null;

            int secondCalcDist = geometryTrimmingService.calculateGeometryDistanceMeters(secondRouteTrimmed);
            Integer secondRouteDistance = secondCalcDist > 0 ? secondCalcDist : null;

            String transferStopName = transferRoute.transferStop().getStopName();
            String secondBoardStopName = transferRoute.secondBoardStop().getStopName();
            String lastStopName = transferRoute.toStop().getStopName();
            String secondRouteNumber = transferRoute.secondRoute().getRouteNumber();

            RouteSegment walkToSeg = routeSegmentFactory.createWalkingSegment(context.fromLocation(), firstStopLocation, walkingToFirst, walkToFirst);
            walkToSeg.setToLocationName(fromStopName);

            RouteSegment firstBusSeg = createBusSegmentWithGeometry(firstStopLocation, transferStopLocation, transferRoute.firstRouteTravelMinutes(), firstRouteNumber, firstRouteTrimmed, firstRouteDistance);
            firstBusSeg.setFromLocationName(fromStopName);
            firstBusSeg.setToLocationName(transferStopName);
            firstBusSeg.setFromStopId(transferRoute.fromStop().getId().toString());
            firstBusSeg.setToStopId(transferRoute.transferStop().getId().toString());

            List<RouteSegment> transferSegments = transferSegmentBuilder.build(
                    transferStopLocation, transferStopName,
                    secondBoardStopLocation, secondBoardStopName,
                    transferRoute.transferWaitMinutes(), transferWalkResult);

            RouteSegment secondBusSeg = createBusSegmentWithGeometry(secondBoardStopLocation, lastStopLocation, transferRoute.secondRouteTravelMinutes(), secondRouteNumber, secondRouteTrimmed, secondRouteDistance);
            secondBusSeg.setFromLocationName(secondBoardStopName);
            secondBusSeg.setToLocationName(lastStopName);
            secondBusSeg.setFromStopId(transferRoute.secondBoardStop().getId().toString());
            secondBusSeg.setToStopId(transferRoute.toStop().getId().toString());

            RouteSegment walkFromSeg = routeSegmentFactory.createWalkingSegment(lastStopLocation, context.toLocation(), walkingFromLast, walkFromLast);
            walkFromSeg.setFromLocationName(lastStopName);

            List<RouteSegment> segments = new ArrayList<>();
            segments.add(walkToSeg);
            segments.add(firstBusSeg);
            segments.addAll(transferSegments);
            segments.add(secondBusSeg);
            segments.add(walkFromSeg);

            log.debug("Creating one-transfer option for routes {}->{} with waiting time {} min",
                    firstRouteNumber, transferRoute.secondRoute().getRouteNumber(), initialWaitingMinutes);

            return tripOptionFactory.createOneTransferOption(segments, initialWaitingMinutes, departureTime);
        });
    }

    private Mono<TripOption> buildTwoTransferOption(RouteCalculationService.TwoTransferRouteResult twoTransferRoute,
                                                    SearchContext context) {

        Coordinates firstStopLocation = createCoordinatesFromStop(twoTransferRoute.fromStop());
        Coordinates firstTransferLocation = createCoordinatesFromStop(twoTransferRoute.firstTransferStop());
        Coordinates secondBoardLocation = createCoordinatesFromStop(twoTransferRoute.secondBoardStop());
        Coordinates secondTransferLocation = createCoordinatesFromStop(twoTransferRoute.secondTransferStop());
        Coordinates thirdBoardLocation = createCoordinatesFromStop(twoTransferRoute.thirdBoardStop());
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

        Mono<WalkingRouteService.WalkingRouteResult> transferWalk1 =
                TransferSegmentBuilder.isSameStop(firstTransferLocation, secondBoardLocation)
                        ? Mono.just(WalkingRouteService.WalkingRouteResult.EMPTY)
                        : walkingRouteService.getWalkingRoute(firstTransferLocation, secondBoardLocation);
        Mono<WalkingRouteService.WalkingRouteResult> transferWalk2 =
                TransferSegmentBuilder.isSameStop(secondTransferLocation, thirdBoardLocation)
                        ? Mono.just(WalkingRouteService.WalkingRouteResult.EMPTY)
                        : walkingRouteService.getWalkingRoute(secondTransferLocation, thirdBoardLocation);

        return Mono.zip(
                etaCalculationService.calculateWaitingTimeMinutes(firstRouteNumber, fromStopName, departureTime),
                walkingRouteService.getWalkingRoute(context.fromLocation(), firstStopLocation),
                walkingRouteService.getWalkingRoute(finalStopLocation, context.toLocation()),
                transferWalk1,
                transferWalk2
        ).map(tuple -> {
            int initialWaitingMinutes = tuple.getT1();
            WalkingRouteService.WalkingRouteResult walkToFirst = tuple.getT2();
            WalkingRouteService.WalkingRouteResult walkFromFinal = tuple.getT3();
            WalkingRouteService.WalkingRouteResult transferWalk1Result = tuple.getT4();
            WalkingRouteService.WalkingRouteResult transferWalk2Result = tuple.getT5();

            String firstRouteGeometry = selectGeometryForDirection(
                    twoTransferRoute.firstRoute(),
                    twoTransferRoute.firstDirection(),
                    twoTransferRoute.fromStop(), twoTransferRoute.firstTransferStop());
            String secondRouteGeometry = selectGeometryForDirection(
                    twoTransferRoute.secondRoute(),
                    twoTransferRoute.secondDirection(),
                    twoTransferRoute.secondBoardStop(), twoTransferRoute.secondTransferStop());
            String thirdRouteGeometry = selectGeometryForDirection(
                    twoTransferRoute.thirdRoute(),
                    twoTransferRoute.thirdDirection(),
                    twoTransferRoute.thirdBoardStop(), twoTransferRoute.toStop());

            String firstRouteTrimmed = trimRouteGeometry(
                    firstRouteGeometry,
                    twoTransferRoute.fromStop(),
                    twoTransferRoute.firstTransferStop()
            );

            String secondRouteTrimmed = trimRouteGeometry(
                    secondRouteGeometry,
                    twoTransferRoute.secondBoardStop(),
                    twoTransferRoute.secondTransferStop()
            );

            String thirdRouteTrimmed = trimRouteGeometry(
                    thirdRouteGeometry,
                    twoTransferRoute.thirdBoardStop(),
                    twoTransferRoute.toStop()
            );

            int firstCalcDist2 = geometryTrimmingService.calculateGeometryDistanceMeters(firstRouteTrimmed);
            Integer firstRouteDistance2 = firstCalcDist2 > 0 ? firstCalcDist2 : null;

            int secondCalcDist2 = geometryTrimmingService.calculateGeometryDistanceMeters(secondRouteTrimmed);
            Integer secondRouteDistance2 = secondCalcDist2 > 0 ? secondCalcDist2 : null;

            int thirdCalcDist2 = geometryTrimmingService.calculateGeometryDistanceMeters(thirdRouteTrimmed);
            Integer thirdRouteDistance2 = thirdCalcDist2 > 0 ? thirdCalcDist2 : null;

            String firstTransferStopName = twoTransferRoute.firstTransferStop().getStopName();
            String secondBoardStopName = twoTransferRoute.secondBoardStop().getStopName();
            String secondTransferStopName = twoTransferRoute.secondTransferStop().getStopName();
            String thirdBoardStopName = twoTransferRoute.thirdBoardStop().getStopName();
            String finalStopName = twoTransferRoute.toStop().getStopName();
            String secondRouteNum = twoTransferRoute.secondRoute().getRouteNumber();
            String thirdRouteNum = twoTransferRoute.thirdRoute().getRouteNumber();

            RouteSegment walkToSeg2 = routeSegmentFactory.createWalkingSegment(context.fromLocation(), firstStopLocation, walkingToFirst, walkToFirst);
            walkToSeg2.setToLocationName(fromStopName);

            RouteSegment firstBusSeg2 = createBusSegmentWithGeometry(firstStopLocation, firstTransferLocation, twoTransferRoute.firstRouteTravelMinutes(), firstRouteNumber, firstRouteTrimmed, firstRouteDistance2);
            firstBusSeg2.setFromLocationName(fromStopName);
            firstBusSeg2.setToLocationName(firstTransferStopName);
            firstBusSeg2.setFromStopId(twoTransferRoute.fromStop().getId().toString());
            firstBusSeg2.setToStopId(twoTransferRoute.firstTransferStop().getId().toString());

            List<RouteSegment> transfer1Segments = transferSegmentBuilder.build(
                    firstTransferLocation, firstTransferStopName,
                    secondBoardLocation, secondBoardStopName,
                    twoTransferRoute.firstTransferWaitMinutes(), transferWalk1Result);

            RouteSegment secondBusSeg2 = createBusSegmentWithGeometry(secondBoardLocation, secondTransferLocation, twoTransferRoute.secondRouteTravelMinutes(), secondRouteNum, secondRouteTrimmed, secondRouteDistance2);
            secondBusSeg2.setFromLocationName(secondBoardStopName);
            secondBusSeg2.setToLocationName(secondTransferStopName);
            secondBusSeg2.setFromStopId(twoTransferRoute.secondBoardStop().getId().toString());
            secondBusSeg2.setToStopId(twoTransferRoute.secondTransferStop().getId().toString());

            List<RouteSegment> transfer2Segments = transferSegmentBuilder.build(
                    secondTransferLocation, secondTransferStopName,
                    thirdBoardLocation, thirdBoardStopName,
                    twoTransferRoute.secondTransferWaitMinutes(), transferWalk2Result);

            RouteSegment thirdBusSeg2 = createBusSegmentWithGeometry(thirdBoardLocation, finalStopLocation, twoTransferRoute.thirdRouteTravelMinutes(), thirdRouteNum, thirdRouteTrimmed, thirdRouteDistance2);
            thirdBusSeg2.setFromLocationName(thirdBoardStopName);
            thirdBusSeg2.setToLocationName(finalStopName);
            thirdBusSeg2.setFromStopId(twoTransferRoute.thirdBoardStop().getId().toString());
            thirdBusSeg2.setToStopId(twoTransferRoute.toStop().getId().toString());

            RouteSegment walkFromSeg2 = routeSegmentFactory.createWalkingSegment(finalStopLocation, context.toLocation(), walkingFromFinal, walkFromFinal);
            walkFromSeg2.setFromLocationName(finalStopName);

            List<RouteSegment> segments = new ArrayList<>();
            segments.add(walkToSeg2);
            segments.add(firstBusSeg2);
            segments.addAll(transfer1Segments);
            segments.add(secondBusSeg2);
            segments.addAll(transfer2Segments);
            segments.add(thirdBusSeg2);
            segments.add(walkFromSeg2);

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

    private String selectGeometryForDirection(BusRoute route, Integer direction,
                                              BusStop fromStop, BusStop toStop) {
        String forwardGeom = route.hasForwardGeometry() ? route.getRouteGeometryForward() : null;
        String backwardGeom = route.hasBackwardGeometry() ? route.getRouteGeometryBackward() : null;
        String chosen = RouteGeometrySelector.select(
                forwardGeom, backwardGeom, direction, fromStop, toStop, geometryTrimmingService);
        if (chosen == null) {
            log.warn("⚠️ No geometry found for route {}", route.getRouteNumber());
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
