package biz.ugur.busroutebackend.routing.domain.model.graph;

/**
 * One hop in a Dijkstra path: from one stop to another.
 * Multiple consecutive segments with the same routeId are collapsed
 * into a single segment by TransitPath.collapsed().
 */
public record TransitPathSegment(
        String fromStopId,
        String toStopId,
        EdgeType type,
        int costMinutes,
        String routeNumber,  // null for walking
        String routeId       // null for walking
) {
    public boolean isBusRide() {
        return type == EdgeType.BUS_RIDE;
    }

    public boolean isWalking() {
        return type == EdgeType.WALKING;
    }
}
