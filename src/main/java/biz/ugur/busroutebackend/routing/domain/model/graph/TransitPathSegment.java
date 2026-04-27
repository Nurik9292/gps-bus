package biz.ugur.busroutebackend.routing.domain.model.graph;

public record TransitPathSegment(
        String fromStopId,
        String toStopId,
        EdgeType type,
        int costMinutes,
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
