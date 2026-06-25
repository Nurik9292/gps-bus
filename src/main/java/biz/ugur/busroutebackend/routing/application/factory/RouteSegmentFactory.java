package biz.ugur.busroutebackend.routing.application.factory;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.domain.services.WalkingGeometryGuard;
import biz.ugur.busroutebackend.routing.domain.services.WalkingRouteService;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import org.springframework.stereotype.Component;

@Component
public class RouteSegmentFactory {

    public RouteSegment createWalkingSegment(Coordinates from, Coordinates to, int durationMinutes) {
        return RouteSegment.walkingSegment(from, to, durationMinutes);
    }

    public RouteSegment createWalkingSegment(Coordinates from, Coordinates to, int durationMinutes,
                                             WalkingRouteService.WalkingRouteResult walkingRoute) {
        double straightLineMeters = DistanceCalculationService.haversineDistanceMeters(
                from.getLatitudeAsDouble(), from.getLongitudeAsDouble(),
                to.getLatitudeAsDouble(), to.getLongitudeAsDouble());

        if (walkingRoute.hasGeometry()
                && !WalkingGeometryGuard.isImplausibleDetour(straightLineMeters, walkingRoute.distanceMeters())) {
            return RouteSegment.walkingSegmentWithGeometry(
                    from, to, durationMinutes,
                    walkingRoute.coordinates(), walkingRoute.distanceMeters());
        }
        return RouteSegment.walkingSegmentWithGeometry(
                from, to, durationMinutes,
                RouteSegment.straightLineGeometry(from, to), (int) Math.round(straightLineMeters));
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
