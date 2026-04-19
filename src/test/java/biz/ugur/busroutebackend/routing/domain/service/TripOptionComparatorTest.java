package biz.ugur.busroutebackend.routing.domain.service;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripSearchCriteria;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TripOptionComparatorTest {

    private static final Coordinates A = Coordinates.of(37.9601, 58.3261);
    private static final Coordinates B = Coordinates.of(37.9701, 58.3361);

    private TripOption directOption(int busMinutes) {
        RouteSegment ride = RouteSegment.busRideSegment(A, B, busMinutes, "29A");
        return new TripOption(TripType.DIRECT, List.of(ride), 0, LocalDateTime.of(2026, 5, 1, 10, 0));
    }

    private TripOption oneTransferOption(int totalMinutes) {
        int ride1 = Math.max(1, totalMinutes / 3);
        int transferWait = 2;
        int ride2 = Math.max(1, totalMinutes - ride1 - transferWait);
        List<RouteSegment> segments = List.of(
                RouteSegment.busRideSegment(A, A, ride1, "1"),
                RouteSegment.transferSegment(A, transferWait),
                RouteSegment.busRideSegment(A, B, ride2, "2"));
        return new TripOption(TripType.ONE_TRANSFER, segments, 0, LocalDateTime.of(2026, 5, 1, 10, 0));
    }

    @Test
    void defaultCriteriaPrefersFewerTransfersFirst() {
        TripOption direct = directOption(20);
        TripOption transfer = oneTransferOption(10);

        TripOptionComparator cmp = new TripOptionComparator(TripSearchCriteria.defaultCriteria());
        assertTrue(cmp.compare(direct, transfer) < 0);
        assertTrue(cmp.isOptionBetter(direct, transfer));
    }

    @Test
    void fastestRouteDowngradesFewerTransfers() {
        TripOption direct = directOption(30);
        TripOption faster = oneTransferOption(15);

        TripOptionComparator cmp = new TripOptionComparator(TripSearchCriteria.fastestRoute());
        assertTrue(cmp.compare(faster, direct) < 0);
    }

    @Test
    void equalOptionsYieldZero() {
        TripOption a = directOption(15);
        TripOption b = directOption(15);
        TripOptionComparator cmp = new TripOptionComparator(TripSearchCriteria.defaultCriteria());
        assertEquals(0, cmp.compare(a, b));
    }

    @Test
    void nullCriteriaFallsBackToDefault() {
        TripOption direct = directOption(20);
        TripOption transfer = oneTransferOption(10);
        TripOptionComparator cmp = new TripOptionComparator(null);
        assertTrue(cmp.compare(direct, transfer) < 0);
    }

    @Test
    void isOptionBetterReturnsFalseWhenEqual() {
        TripOption a = directOption(10);
        TripOption b = directOption(10);
        TripOptionComparator cmp = new TripOptionComparator(TripSearchCriteria.defaultCriteria());
        assertFalse(cmp.isOptionBetter(a, b));
    }
}
