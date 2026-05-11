package biz.ugur.busroutebackend.routing.domain.model.raptor;

import biz.ugur.busroutebackend.transport.domain.enums.RouteDirection;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;

public record RaptorLeg(LegType type,
                         BusStopId fromStop,
                         BusStopId toStop,
                         int startSec,
                         int endSec,
                         BusRouteId routeId,
                         RouteDirection direction) {

    public RaptorLeg {
        if (type == null) {
            throw new IllegalArgumentException("RaptorLeg type required");
        }
        if (fromStop == null || toStop == null) {
            throw new IllegalArgumentException("RaptorLeg stops required");
        }
        if (endSec < startSec) {
            throw new IllegalArgumentException(
                    "RaptorLeg endSec (" + endSec + ") < startSec (" + startSec + ")");
        }
        if (type == LegType.BUS && (routeId == null || direction == null)) {
            throw new IllegalArgumentException(
                    "RaptorLeg BUS requires routeId and direction");
        }
        if (type == LegType.WALK && (routeId != null || direction != null)) {
            throw new IllegalArgumentException(
                    "RaptorLeg WALK must not carry routeId or direction");
        }
    }

    public static RaptorLeg bus(BusStopId from, BusStopId to, int startSec, int endSec,
                                 BusRouteId routeId, RouteDirection direction) {
        return new RaptorLeg(LegType.BUS, from, to, startSec, endSec, routeId, direction);
    }

    public static RaptorLeg walk(BusStopId from, BusStopId to, int startSec, int endSec) {
        return new RaptorLeg(LegType.WALK, from, to, startSec, endSec, null, null);
    }

    public int durationSec() {
        return endSec - startSec;
    }

    public enum LegType { BUS, WALK }
}
