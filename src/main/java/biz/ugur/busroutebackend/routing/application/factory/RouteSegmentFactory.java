package biz.ugur.busroutebackend.routing.application.factory;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import org.springframework.stereotype.Component;

@Component
public class RouteSegmentFactory {

    public RouteSegment createWalkingSegment(Coordinates from, Coordinates to, int durationMinutes) {
        return RouteSegment.walkingSegment(from, to, durationMinutes);
    }

    public RouteSegment createBusRideSegment(Coordinates from, Coordinates to, int durationMinutes, String routeNumber) {
        return RouteSegment.busRideSegment(from, to, durationMinutes, routeNumber);
    }

    public RouteSegment createBusRideSegmentWithGeometry(
            Coordinates from,
            Coordinates to,
            int durationMinutes,
            String routeNumber,
            String routeGeometryWkt,
            Integer distanceMeters) {

        return RouteSegment.busRideSegmentWithGeometry(
                from, to, durationMinutes, routeNumber, routeGeometryWkt, distanceMeters
        );
    }

    public RouteSegment createTransferSegment(Coordinates transferLocation, int waitMinutes) {
        return RouteSegment.transferSegment(transferLocation, waitMinutes);
    }
}
