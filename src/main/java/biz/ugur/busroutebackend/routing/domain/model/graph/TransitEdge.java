package biz.ugur.busroutebackend.routing.domain.model.graph;

/**
 * Directed edge in the transit graph.
 * Represents either a bus-ride segment (from stop to next stop on the same route)
 * or a walking segment (between two stops within walking distance).
 */
public record TransitEdge(
        String toStopId,
        EdgeType type,
        int weightMinutes,
        String routeNumber,  // null for walking edges
        String routeId       // null for walking edges
) {
    public boolean isBusRide() {
        return type == EdgeType.BUS_RIDE;
    }

    public boolean isWalking() {
        return type == EdgeType.WALKING;
    }
}
