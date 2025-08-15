package biz.ugur.busroutebackend.routing.application.builders;

import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.services.ETACalculationService;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import biz.ugur.busroutebackend.routing.domain.volumeojects.Location;
import biz.ugur.busroutebackend.routing.domain.volumeojects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.volumeojects.TripOption;
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

    public TransferRouteOptionBuilder(ETACalculationService etaCalculationService, WalkingTimeCalculator walkingTimeCalculator) {
        this.etaCalculationService = etaCalculationService;
        this.walkingTimeCalculator = walkingTimeCalculator;
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

        List<RouteSegment> segments = List.of(
                RouteSegment.walkingSegment(context.fromLocation(), firstStopLocation, walkingToFirst),
                RouteSegment.busRideSegment(firstStopLocation, transferStopLocation,
                        transferRoute.firstRouteTravelMinutes(), transferRoute.firstRoute().getRouteNumber()),
                RouteSegment.transferSegment(transferStopLocation, transferRoute.transferWaitMinutes()),
                RouteSegment.busRideSegment(transferStopLocation, lastStopLocation,
                        transferRoute.secondRouteTravelMinutes(), transferRoute.secondRoute().getRouteNumber()),
                RouteSegment.walkingSegment(lastStopLocation, context.toLocation(), walkingFromLast)
        );

        return new TripOption(TripType.ONE_TRANSFER, segments);
    }

    private TripOption buildTwoTransferOption(RouteCalculationService.TwoTransferRouteResult twoTransferRoute,
                                              SearchContext context) {
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

        List<RouteSegment> segments = List.of(
                RouteSegment.walkingSegment(context.fromLocation(), firstStopLocation, walkingToFirst),
                RouteSegment.busRideSegment(firstStopLocation, firstTransferLocation,
                        twoTransferRoute.firstRouteTravelMinutes(), twoTransferRoute.firstRoute().getRouteNumber()),
                RouteSegment.transferSegment(firstTransferLocation, twoTransferRoute.firstTransferWaitMinutes()),
                RouteSegment.busRideSegment(firstTransferLocation, secondTransferLocation,
                        twoTransferRoute.secondRouteTravelMinutes(), twoTransferRoute.secondRoute().getRouteNumber()),
                RouteSegment.transferSegment(secondTransferLocation, twoTransferRoute.secondTransferWaitMinutes()),
                RouteSegment.busRideSegment(secondTransferLocation, finalStopLocation,
                        twoTransferRoute.thirdRouteTravelMinutes(), twoTransferRoute.thirdRoute().getRouteNumber()),
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
}
