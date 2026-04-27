package biz.ugur.busroutebackend.routing.domain.model.graph;


public record TransitEdge(
        String toStopId,
        EdgeType type,
        int weightMinutes,
        String routeNumber,
        String routeId,
        Integer direction
) {
    public boolean isBusRide() {
        return type == EdgeType.BUS_RIDE;
    }

    public boolean isWalking() {
        return type == EdgeType.WALKING;
    }
}
