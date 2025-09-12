package biz.ugur.busroutebackend.routing.application.builders;

import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.services.ETACalculationService;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.routing.domain.valueobjects.Location;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import biz.ugur.busroutebackend.routing.infrastructure.services.RouteGeometryTrimmingService;
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

    public TransferRouteOptionBuilder(ETACalculationService etaCalculationService,
                                      WalkingTimeCalculator walkingTimeCalculator,
                                      RouteGeometryTrimmingService geometryTrimmingService) {
        this.etaCalculationService = etaCalculationService;
        this.walkingTimeCalculator = walkingTimeCalculator;
        this.geometryTrimmingService = geometryTrimmingService;
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
        log.debug("Building one-transfer option:");
        log.debug("  First route geometry: {}",
                transferRoute.firstRoute().getRouteGeometryForward() != null ? "PRESENT" : "NULL");
        log.debug("  Second route geometry: {}",
                transferRoute.secondRoute().getRouteGeometryForward() != null ? "PRESENT" : "NULL");
        Location firstStopLocation = createLocationFromStop(transferRoute.fromStop());
        Location transferStopLocation = createLocationFromStop(transferRoute.transferStop());
        Location lastStopLocation = createLocationFromStop(transferRoute.toStop());

        int walkingToFirst = walkingTimeCalculator.calculateWalkingTime(
                context.fromLocation(), firstStopLocation);
        int walkingFromLast = walkingTimeCalculator.calculateWalkingTime(
                lastStopLocation, context.toLocation());

        if (walkingToFirst > 15 || walkingFromLast > 15) {
            throw new IllegalArgumentException("Walking time too long");
        }

        String firstRouteGeometry = trimRouteGeometry(
                transferRoute.firstRoute().getRouteGeometryForward(),
                transferRoute.fromStop(),
                transferRoute.transferStop()
        );

        String secondRouteGeometry = trimRouteGeometry(
                transferRoute.secondRoute().getRouteGeometryForward(),
                transferRoute.transferStop(),
                transferRoute.toStop()
        );

        List<RouteSegment> segments = List.of(
                RouteSegment.walkingSegment(context.fromLocation(), firstStopLocation, walkingToFirst),
                createBusSegmentWithGeometry(firstStopLocation, transferStopLocation,
                        transferRoute.firstRouteTravelMinutes(),
                        transferRoute.firstRoute().getRouteNumber(),
                        firstRouteGeometry,
                        transferRoute.firstRoute().getTotalDistanceForwardMeters()),
                RouteSegment.transferSegment(transferStopLocation, transferRoute.transferWaitMinutes()),
                createBusSegmentWithGeometry(transferStopLocation, lastStopLocation,
                        transferRoute.secondRouteTravelMinutes(),
                        transferRoute.secondRoute().getRouteNumber(),
                        secondRouteGeometry,
                        transferRoute.secondRoute().getTotalDistanceForwardMeters()),
                RouteSegment.walkingSegment(lastStopLocation, context.toLocation(), walkingFromLast)
        );

        return new TripOption(TripType.ONE_TRANSFER, segments);
    }

    private TripOption buildTwoTransferOption(RouteCalculationService.TwoTransferRouteResult twoTransferRoute,
                                              SearchContext context) {
        log.debug("Building one-transfer option:");
        log.debug("  First route geometry: {}",
                twoTransferRoute.firstRoute().getRouteGeometryForward() != null ? "PRESENT" : "NULL");
        log.debug("  Second route geometry: {}",
                twoTransferRoute.secondRoute().getRouteGeometryForward() != null ? "PRESENT" : "NULL");

        Location firstStopLocation = createLocationFromStop(twoTransferRoute.fromStop());
        Location firstTransferLocation = createLocationFromStop(twoTransferRoute.firstTransferStop());
        Location secondTransferLocation = createLocationFromStop(twoTransferRoute.secondTransferStop());
        Location finalStopLocation = createLocationFromStop(twoTransferRoute.toStop());

        int walkingToFirst = walkingTimeCalculator.calculateWalkingTime(
                context.fromLocation(), firstStopLocation);
        int walkingFromFinal = walkingTimeCalculator.calculateWalkingTime(
                finalStopLocation, context.toLocation());

        if (walkingToFirst > 18 || walkingFromFinal > 18) {
            throw new IllegalArgumentException("Walking time too long for two transfers");
        }

        String firstRouteGeometry = trimRouteGeometry(
                twoTransferRoute.firstRoute().getRouteGeometryForward(),
                twoTransferRoute.fromStop(),
                twoTransferRoute.firstTransferStop()
        );

        String secondRouteGeometry = trimRouteGeometry(
                twoTransferRoute.secondRoute().getRouteGeometryForward(),
                twoTransferRoute.firstTransferStop(),
                twoTransferRoute.secondTransferStop()
        );

        String thirdRouteGeometry = trimRouteGeometry(
                twoTransferRoute.thirdRoute().getRouteGeometryForward(),
                twoTransferRoute.secondTransferStop(),
                twoTransferRoute.toStop()
        );

        List<RouteSegment> segments = List.of(
                RouteSegment.walkingSegment(context.fromLocation(), firstStopLocation, walkingToFirst),
                createBusSegmentWithGeometry(firstStopLocation, firstTransferLocation,
                        twoTransferRoute.firstRouteTravelMinutes(),
                        twoTransferRoute.firstRoute().getRouteNumber(),
                        firstRouteGeometry,
                        twoTransferRoute.firstRoute().getTotalDistanceForwardMeters()),
                RouteSegment.transferSegment(firstTransferLocation, twoTransferRoute.firstTransferWaitMinutes()),
                createBusSegmentWithGeometry(firstTransferLocation, secondTransferLocation,
                        twoTransferRoute.secondRouteTravelMinutes(),
                        twoTransferRoute.secondRoute().getRouteNumber(),
                        secondRouteGeometry,
                        twoTransferRoute.secondRoute().getTotalDistanceForwardMeters()),
                RouteSegment.transferSegment(secondTransferLocation, twoTransferRoute.secondTransferWaitMinutes()),
                createBusSegmentWithGeometry(secondTransferLocation, finalStopLocation,
                        twoTransferRoute.thirdRouteTravelMinutes(),
                        twoTransferRoute.thirdRoute().getRouteNumber(),
                        thirdRouteGeometry,
                        twoTransferRoute.thirdRoute().getTotalDistanceForwardMeters()),
                RouteSegment.walkingSegment(finalStopLocation, context.toLocation(), walkingFromFinal)
        );

        return new TripOption(TripType.TWO_TRANSFERS, segments);
    }

    private Location createLocationFromStop(BusStop stop) {
        return new Location(
                stop.getLatitude().doubleValue(),
                stop.getLongitude().doubleValue(),
                stop.getStopName()
        );
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

    private RouteSegment createBusSegmentWithGeometry(Location from, Location to, int durationMinutes,
                                                      String routeNumber, String geometry, Integer distance) {
        if (geometry != null) {
            return RouteSegment.busRideSegmentWithGeometry(from, to, durationMinutes, routeNumber, geometry, distance);
        } else {
            return RouteSegment.busRideSegment(from, to, durationMinutes, routeNumber);
        }
    }
}
