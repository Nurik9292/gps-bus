package biz.ugur.busroutebackend.routing.application.builders;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.application.factory.RouteSegmentFactory;
import biz.ugur.busroutebackend.routing.domain.services.WalkingRouteService;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TransferSegmentBuilder {

    private static final double SAME_STOP_THRESHOLD_METERS = 15.0;

    private final WalkingTimeCalculator walkingTimeCalculator;
    private final RouteSegmentFactory routeSegmentFactory;

    public TransferSegmentBuilder(WalkingTimeCalculator walkingTimeCalculator,
                                  RouteSegmentFactory routeSegmentFactory) {
        this.walkingTimeCalculator = walkingTimeCalculator;
        this.routeSegmentFactory = routeSegmentFactory;
    }

    public List<RouteSegment> build(Coordinates alightLocation, String alightName,
                                    Coordinates boardLocation, String boardName,
                                    int transferWaitMinutes) {
        double transferWalkMeters = DistanceCalculationService.haversineDistanceMeters(
                alightLocation.getLatitudeAsDouble(), alightLocation.getLongitudeAsDouble(),
                boardLocation.getLatitudeAsDouble(), boardLocation.getLongitudeAsDouble());

        if (transferWalkMeters < SAME_STOP_THRESHOLD_METERS) {
            return List.of(transferAt(boardLocation, boardName, transferWaitMinutes));
        }

        int walkMinutes = walkingTimeCalculator.calculateWalkingTime(alightLocation, boardLocation);
        int waitMinutes = Math.max(0, transferWaitMinutes - walkMinutes);

        RouteSegment walk = routeSegmentFactory.createWalkingSegment(
                alightLocation, boardLocation, walkMinutes,
                WalkingRouteService.WalkingRouteResult.EMPTY);
        walk.setFromLocationName(alightName);
        walk.setToLocationName(boardName);

        return List.of(walk, transferAt(boardLocation, boardName, waitMinutes));
    }

    private RouteSegment transferAt(Coordinates location, String name, int waitMinutes) {
        RouteSegment transfer = routeSegmentFactory.createTransferSegment(location, waitMinutes);
        transfer.setFromLocationName(name);
        transfer.setToLocationName(name);
        return transfer;
    }
}
