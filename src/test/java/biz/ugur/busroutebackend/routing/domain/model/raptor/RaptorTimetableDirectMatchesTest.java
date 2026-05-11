package biz.ugur.busroutebackend.routing.domain.model.raptor;

import biz.ugur.busroutebackend.transport.domain.enums.RouteDirection;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaptorTimetableDirectMatchesTest {

    private static final BusStopId A = BusStopId.of("stop-A");
    private static final BusStopId B = BusStopId.of("stop-B");
    private static final BusStopId C = BusStopId.of("stop-C");
    private static final BusStopId D = BusStopId.of("stop-D");

    @Test
    void enumeratesAllRoutesWithBothStopsInForwardOrder() {
        RaptorRoute r1 = makeRoute("R1", RouteDirection.FORWARD, List.of(A, B, C, D));
        RaptorRoute r2 = makeRoute("R2", RouteDirection.FORWARD, List.of(A, C));
        RaptorRoute r3 = makeRoute("R3", RouteDirection.FORWARD, List.of(B, A, D));
        RaptorTimetable tt = RaptorTimetable.from(List.of(r1, r2, r3), List.of());

        List<RaptorTimetable.DirectMatch> matches = tt.directMatches(A, C);

        Set<String> routeIds = collectRouteIds(matches);
        assertEquals(Set.of("R1", "R2"), routeIds);
    }

    @Test
    void rejectsReverseOrder() {
        RaptorRoute r = makeRoute("R1", RouteDirection.FORWARD, List.of(A, B, C));
        RaptorTimetable tt = RaptorTimetable.from(List.of(r), List.of());

        assertTrue(tt.directMatches(C, A).isEmpty());
    }

    @Test
    void returnsEmptyWhenStopMissing() {
        RaptorRoute r = makeRoute("R1", RouteDirection.FORWARD, List.of(A, B));
        RaptorTimetable tt = RaptorTimetable.from(List.of(r), List.of());

        assertTrue(tt.directMatches(A, D).isEmpty());
        assertTrue(tt.directMatches(D, A).isEmpty());
    }

    @Test
    void distinctRoutesPerDirection_returnsTwoSeparateMatches() {
        RaptorRoute fwd = makeRoute("R1", RouteDirection.FORWARD, List.of(A, B, C));
        RaptorRoute bwd = makeRoute("R1", RouteDirection.BACKWARD, List.of(C, B, A));
        RaptorTimetable tt = RaptorTimetable.from(List.of(fwd, bwd), List.of());

        List<RaptorTimetable.DirectMatch> fromA = tt.directMatches(A, C);
        List<RaptorTimetable.DirectMatch> fromC = tt.directMatches(C, A);

        assertEquals(1, fromA.size());
        assertEquals(RouteDirection.FORWARD, fromA.get(0).route().direction());
        assertEquals(1, fromC.size());
        assertEquals(RouteDirection.BACKWARD, fromC.get(0).route().direction());
    }

    private RaptorRoute makeRoute(String routeId, RouteDirection direction, List<BusStopId> stops) {
        List<RaptorStopTime> stopTimes = new java.util.ArrayList<>();
        for (int i = 0; i < stops.size(); i++) {
            stopTimes.add(RaptorStopTime.instant(i * 600));
        }
        List<RaptorTrip> trips = List.of(new RaptorTrip(TripId.generate(), 6 * 3600));
        return new RaptorRoute(BusRouteId.of(routeId), direction, stops, stopTimes, trips);
    }

    private Set<String> collectRouteIds(List<RaptorTimetable.DirectMatch> matches) {
        Set<String> ids = new HashSet<>();
        for (RaptorTimetable.DirectMatch m : matches) {
            ids.add(m.route().routeId().getValue());
        }
        return ids;
    }
}
