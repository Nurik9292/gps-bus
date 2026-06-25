package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorJourney;
import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorLeg;
import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorTimetable;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService.TransferRouteResult;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService.TwoTransferRouteResult;
import biz.ugur.busroutebackend.transport.domain.enums.RouteDirection;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RaptorJourneyMapperTransferBoardTest {

    private final RaptorJourneyMapper mapper = new RaptorJourneyMapper();

    private BusStop stop(String id) {
        return BusStop.restore(BusStopId.of(id), id, null, null, null,
                BigDecimal.valueOf(37.9), BigDecimal.valueOf(58.4),
                true, false, null, null, null, 0L);
    }

    private BusRoute route(String number) {
        return BusRoute.restore(BusRouteId.of("route-" + number), number, "Route " + number,
                null, null, "#1976D2", true, null, 20,
                null, null, null, null, null, null, 0L);
    }

    @Test
    void oneTransferUsesSecondBusActualBoardingStop_notFirstBusAlightStop() {
        RaptorTimetable tt = mock(RaptorTimetable.class);
        BusRouteId r1 = BusRouteId.of("route-160");
        BusRouteId r2 = BusRouteId.of("route-57");
        BusStopId board = BusStopId.of("stop-board");
        BusStopId alight1 = BusStopId.of("stop-984");
        BusStopId board2 = BusStopId.of("stop-8");
        BusStopId alight2 = BusStopId.of("stop-172");

        when(tt.busRouteOf(r1)).thenReturn(route("160"));
        when(tt.busRouteOf(r2)).thenReturn(route("57"));
        when(tt.busStopOf(board)).thenReturn(stop("stop-board"));
        when(tt.busStopOf(alight1)).thenReturn(stop("stop-984"));
        when(tt.busStopOf(board2)).thenReturn(stop("stop-8"));
        when(tt.busStopOf(alight2)).thenReturn(stop("stop-172"));

        RaptorJourney journey = new RaptorJourney(board, alight2, 0, 900, 1, List.of(
                RaptorLeg.bus(board, alight1, 0, 600, r1, RouteDirection.FORWARD),
                RaptorLeg.walk(alight1, board2, 600, 739),
                RaptorLeg.bus(board2, alight2, 739, 850, r2, RouteDirection.FORWARD)));

        Optional<TransferRouteResult> result = mapper.toOneTransfer(journey, tt);

        assertThat(result).isPresent();
        assertThat(result.get().transferStop().getId().getValue()).isEqualTo("stop-984");
        assertThat(result.get().secondBoardStop().getId().getValue()).isEqualTo("stop-8");
    }

    @Test
    void twoTransferUsesSecondAndThirdBusActualBoardingStops() {
        RaptorTimetable tt = mock(RaptorTimetable.class);
        BusRouteId r1 = BusRouteId.of("route-160");
        BusRouteId r2 = BusRouteId.of("route-57");
        BusRouteId r3 = BusRouteId.of("route-37");
        BusStopId board = BusStopId.of("b0");
        BusStopId t1 = BusStopId.of("t1");
        BusStopId b2 = BusStopId.of("b2");
        BusStopId t2 = BusStopId.of("t2");
        BusStopId b3 = BusStopId.of("b3");
        BusStopId end = BusStopId.of("end");

        when(tt.busRouteOf(r1)).thenReturn(route("160"));
        when(tt.busRouteOf(r2)).thenReturn(route("57"));
        when(tt.busRouteOf(r3)).thenReturn(route("37"));
        when(tt.busStopOf(board)).thenReturn(stop("b0"));
        when(tt.busStopOf(t1)).thenReturn(stop("t1"));
        when(tt.busStopOf(b2)).thenReturn(stop("b2"));
        when(tt.busStopOf(t2)).thenReturn(stop("t2"));
        when(tt.busStopOf(b3)).thenReturn(stop("b3"));
        when(tt.busStopOf(end)).thenReturn(stop("end"));

        RaptorJourney journey = new RaptorJourney(board, end, 0, 1500, 2, List.of(
                RaptorLeg.bus(board, t1, 0, 500, r1, RouteDirection.FORWARD),
                RaptorLeg.walk(t1, b2, 500, 600),
                RaptorLeg.bus(b2, t2, 600, 1000, r2, RouteDirection.FORWARD),
                RaptorLeg.walk(t2, b3, 1000, 1100),
                RaptorLeg.bus(b3, end, 1100, 1400, r3, RouteDirection.FORWARD)));

        Optional<TwoTransferRouteResult> result = mapper.toTwoTransfers(journey, tt);

        assertThat(result).isPresent();
        assertThat(result.get().firstTransferStop().getId().getValue()).isEqualTo("t1");
        assertThat(result.get().secondBoardStop().getId().getValue()).isEqualTo("b2");
        assertThat(result.get().secondTransferStop().getId().getValue()).isEqualTo("t2");
        assertThat(result.get().thirdBoardStop().getId().getValue()).isEqualTo("b3");
    }
}
