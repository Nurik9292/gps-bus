package biz.ugur.busroutebackend.routing.infrastructure.raptor;

import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorJourney;
import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorLeg;
import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorRoute;
import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorStopTime;
import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorTimetable;
import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorTransfer;
import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorTrip;
import biz.ugur.busroutebackend.routing.domain.model.raptor.TripId;
import biz.ugur.busroutebackend.transport.domain.enums.RouteDirection;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaptorEngineTest {

    private static final BusStopId A = BusStopId.of("stop-A");
    private static final BusStopId B = BusStopId.of("stop-B");
    private static final BusStopId C = BusStopId.of("stop-C");
    private static final BusStopId D = BusStopId.of("stop-D");
    private static final BusStopId UNREACHABLE = BusStopId.of("stop-X");

    private static final int DEPARTURE_06_00 = 6 * 3600;
    private static final int MAX_ROUNDS = 4;

    private final RaptorEngine engine = new RaptorEngine();

    @Test
    void directRoute_findsSingleBusLeg() {
        RaptorRoute route1 = makeRoute("R1", RouteDirection.FORWARD,
                List.of(A, B, C),
                List.of(0, 600, 1200),
                List.of(DEPARTURE_06_00));
        RaptorTimetable tt = RaptorTimetable.from(List.of(route1), List.of());

        List<RaptorJourney> journeys = engine.findJourneys(tt, A, C, DEPARTURE_06_00, MAX_ROUNDS);

        assertEquals(1, journeys.size());
        RaptorJourney j = journeys.get(0);
        assertEquals(1, j.busLegCount());
        assertEquals(0, j.numTransfers());
        assertEquals(DEPARTURE_06_00 + 1200, j.arrivalTimeSec());
        assertEquals(RaptorLeg.LegType.BUS, j.legs().get(0).type());
        assertEquals(A, j.legs().get(0).fromStop());
        assertEquals(C, j.legs().get(0).toStop());
    }

    @Test
    void oneTransfer_findsTwoBusLegs() {
        RaptorRoute route1 = makeRoute("R1", RouteDirection.FORWARD,
                List.of(A, B),
                List.of(0, 600),
                List.of(DEPARTURE_06_00));
        RaptorRoute route2 = makeRoute("R2", RouteDirection.FORWARD,
                List.of(B, C),
                List.of(0, 600),
                List.of(DEPARTURE_06_00 + 600));
        RaptorTimetable tt = RaptorTimetable.from(List.of(route1, route2), List.of());

        List<RaptorJourney> journeys = engine.findJourneys(tt, A, C, DEPARTURE_06_00, MAX_ROUNDS);

        assertFalse(journeys.isEmpty());
        RaptorJourney best = journeys.get(journeys.size() - 1);
        assertEquals(2, best.busLegCount());
        assertEquals(1, best.numTransfers());
        assertEquals(DEPARTURE_06_00 + 1200, best.arrivalTimeSec());
    }

    @Test
    void twoTransfers_findsThreeBusLegs() {
        RaptorRoute r1 = makeRoute("R1", RouteDirection.FORWARD,
                List.of(A, B), List.of(0, 300), List.of(DEPARTURE_06_00));
        RaptorRoute r2 = makeRoute("R2", RouteDirection.FORWARD,
                List.of(B, C), List.of(0, 300), List.of(DEPARTURE_06_00 + 300));
        RaptorRoute r3 = makeRoute("R3", RouteDirection.FORWARD,
                List.of(C, D), List.of(0, 300), List.of(DEPARTURE_06_00 + 600));
        RaptorTimetable tt = RaptorTimetable.from(List.of(r1, r2, r3), List.of());

        List<RaptorJourney> journeys = engine.findJourneys(tt, A, D, DEPARTURE_06_00, MAX_ROUNDS);

        assertFalse(journeys.isEmpty());
        RaptorJourney best = journeys.get(journeys.size() - 1);
        assertEquals(3, best.busLegCount());
        assertEquals(2, best.numTransfers());
        assertEquals(DEPARTURE_06_00 + 900, best.arrivalTimeSec());
    }

    @Test
    void unreachableStop_returnsEmpty() {
        RaptorRoute route1 = makeRoute("R1", RouteDirection.FORWARD,
                List.of(A, B), List.of(0, 600), List.of(DEPARTURE_06_00));
        RaptorTimetable tt = RaptorTimetable.from(List.of(route1), List.of());

        List<RaptorJourney> journeys = engine.findJourneys(tt, A, UNREACHABLE, DEPARTURE_06_00, MAX_ROUNDS);
        assertTrue(journeys.isEmpty());
    }

    @Test
    void footpathFromOrigin_lowersInitialArrivalAtNeighbor() {
        RaptorRoute route = makeRoute("R1", RouteDirection.FORWARD,
                List.of(B, C),
                List.of(0, 600),
                List.of(DEPARTURE_06_00 + 600));
        RaptorTransfer footAB = new RaptorTransfer(A, B, 120, 100);
        RaptorTimetable tt = RaptorTimetable.from(List.of(route), List.of(footAB));

        List<RaptorJourney> journeys = engine.findJourneys(tt, A, C, DEPARTURE_06_00, MAX_ROUNDS);

        assertFalse(journeys.isEmpty());
        RaptorJourney j = journeys.get(0);
        assertEquals(1, j.busLegCount());
        assertEquals(RaptorLeg.LegType.WALK, j.legs().get(0).type());
        assertEquals(RaptorLeg.LegType.BUS, j.legs().get(1).type());
        assertEquals(DEPARTURE_06_00 + 600 + 600, j.arrivalTimeSec());
    }

    @Test
    void departureLaterThanLastTrip_returnsEmpty() {
        RaptorRoute route = makeRoute("R1", RouteDirection.FORWARD,
                List.of(A, B), List.of(0, 600), List.of(DEPARTURE_06_00));
        RaptorTimetable tt = RaptorTimetable.from(List.of(route), List.of());

        List<RaptorJourney> journeys = engine.findJourneys(tt, A, B,
                DEPARTURE_06_00 + 3600, MAX_ROUNDS);
        assertTrue(journeys.isEmpty());
    }

    private RaptorRoute makeRoute(String routeId,
                                   RouteDirection direction,
                                   List<BusStopId> stops,
                                   List<Integer> offsetsSec,
                                   List<Integer> tripStartTimesSec) {
        List<RaptorStopTime> stopTimes = offsetsSec.stream().map(RaptorStopTime::instant).toList();
        List<RaptorTrip> trips = tripStartTimesSec.stream()
                .map(t -> new RaptorTrip(TripId.generate(), t))
                .toList();
        return new RaptorRoute(BusRouteId.of(routeId), direction, stops, stopTimes, trips);
    }
}
