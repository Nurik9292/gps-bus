package biz.ugur.busroutebackend.routing.domain.model.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * A complete path from origin stop to destination stop found by Dijkstra.
 * Segments represent individual stop-to-stop hops.
 */
public record TransitPath(
        List<TransitPathSegment> segments,
        int totalCostMinutes,
        int transfers
) {
    /**
     * Returns segments with consecutive BUS_RIDE hops on the same route merged
     * into a single segment. Walking segments are left as-is.
     */
    public List<TransitPathSegment> collapsed() {
        if (segments.isEmpty()) return Collections.emptyList();

        List<TransitPathSegment> result = new ArrayList<>();
        TransitPathSegment current = segments.get(0);

        for (int i = 1; i < segments.size(); i++) {
            TransitPathSegment next = segments.get(i);
            if (current.isBusRide() && next.isBusRide()
                    && Objects.equals(current.routeId(), next.routeId())) {
                // Merge consecutive same-route hops into one segment
                current = new TransitPathSegment(
                        current.fromStopId(),
                        next.toStopId(),
                        EdgeType.BUS_RIDE,
                        current.costMinutes() + next.costMinutes(),
                        current.routeNumber(),
                        current.routeId()
                );
            } else {
                result.add(current);
                current = next;
            }
        }
        result.add(current);
        return result;
    }

    /**
     * Number of distinct bus segments in the collapsed path.
     */
    public int busSegmentCount() {
        return (int) collapsed().stream().filter(TransitPathSegment::isBusRide).count();
    }

    /**
     * Set of route IDs used (bus routes only).
     */
    public Set<String> usedRouteIds() {
        Set<String> ids = new HashSet<>();
        for (TransitPathSegment seg : segments) {
            if (seg.isBusRide() && seg.routeId() != null) {
                ids.add(seg.routeId());
            }
        }
        return ids;
    }
}
