package biz.ugur.busroutebackend.routing.domain.model.graph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;


public record TransitPath(
        List<TransitPathSegment> segments,
        int totalCostMinutes,
        int transfers
) {

    public List<TransitPathSegment> collapsed() {
        if (segments.isEmpty()) return Collections.emptyList();

        List<TransitPathSegment> result = new ArrayList<>();
        TransitPathSegment current = segments.get(0);

        for (int i = 1; i < segments.size(); i++) {
            TransitPathSegment next = segments.get(i);
            if (current.isBusRide() && next.isBusRide()
                    && Objects.equals(current.routeId(), next.routeId())
                    && Objects.equals(current.direction(), next.direction())) {
                current = new TransitPathSegment(
                        current.fromStopId(),
                        next.toStopId(),
                        EdgeType.BUS_RIDE,
                        current.costMinutes() + next.costMinutes(),
                        current.routeNumber(),
                        current.routeId(),
                        current.direction()
                );
            } else {
                result.add(current);
                current = next;
            }
        }
        result.add(current);
        return result;
    }


    public int busSegmentCount() {
        return (int) collapsed().stream().filter(TransitPathSegment::isBusRide).count();
    }


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
