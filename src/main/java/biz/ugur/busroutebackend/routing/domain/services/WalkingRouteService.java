package biz.ugur.busroutebackend.routing.domain.services;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import reactor.core.publisher.Mono;

import java.util.List;

public interface WalkingRouteService {

    Mono<WalkingRouteResult> getWalkingRoute(Coordinates from, Coordinates to);

    record WalkingRouteResult(List<List<Double>> coordinates, int distanceMeters) {

        public static final WalkingRouteResult EMPTY = new WalkingRouteResult(null, 0);

        public boolean hasGeometry() {
            return coordinates != null && !coordinates.isEmpty();
        }
    }
}
