package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.transport.domain.model.BusStop;

public final class RouteGeometrySelector {

    private RouteGeometrySelector() {}

    public static String select(String forwardGeom,
                                String backwardGeom,
                                Integer direction,
                                BusStop fromStop,
                                BusStop toStop,
                                RouteGeometryTrimmingService heuristic) {
        if (forwardGeom == null && backwardGeom == null) {
            return null;
        }

        if (direction != null) {
            String chosen = direction == 0 ? forwardGeom : backwardGeom;
            if (chosen != null) {
                return chosen;
            }
            return forwardGeom != null ? forwardGeom : backwardGeom;
        }

        return heuristic.selectGeometryForDirection(forwardGeom, backwardGeom, fromStop, toStop);
    }
}
