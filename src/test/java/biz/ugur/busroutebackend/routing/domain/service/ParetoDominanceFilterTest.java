package biz.ugur.busroutebackend.routing.domain.service;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ParetoDominanceFilterTest {

    private static final Coordinates A = Coordinates.of(37.9601, 58.3261);
    private static final Coordinates B = Coordinates.of(37.9701, 58.3361);
    private static final LocalDateTime T = LocalDateTime.of(2026, 5, 1, 10, 0);

    private final ParetoDominanceFilter filter = new ParetoDominanceFilter();

    private TripOption directOption(int busMinutes) {
        RouteSegment ride = RouteSegment.busRideSegment(A, B, busMinutes, "29A");
        return new TripOption(TripType.DIRECT, List.of(ride), 0, T);
    }

    private TripOption oneTransferOption(int totalMinutes) {
        int ride1 = Math.max(1, totalMinutes / 3);
        int transferWait = 2;
        int ride2 = Math.max(1, totalMinutes - ride1 - transferWait);
        List<RouteSegment> segments = List.of(
                RouteSegment.busRideSegment(A, A, ride1, "1"),
                RouteSegment.transferSegment(A, transferWait),
                RouteSegment.busRideSegment(A, B, ride2, "2"));
        return new TripOption(TripType.ONE_TRANSFER, segments, 0, T);
    }

    private TripOption walkingOnlyOption(int walkMinutes) {
        RouteSegment walk = RouteSegment.walkingSegment(A, B, walkMinutes);
        return new TripOption(TripType.WALKING_ONLY, List.of(walk), 0, T);
    }

    @Test
    void removesTransfersDominatedByFasterDirect() {
        TripOption direct = directOption(35);
        TripOption slowTransfer1 = oneTransferOption(52);
        TripOption slowTransfer2 = oneTransferOption(69);

        List<TripOption> result = filter.filterDominated(List.of(direct, slowTransfer1, slowTransfer2));

        assertThat(result).containsExactly(direct);
    }

    @Test
    void keepsWalkingOnlyEvenWhenDominated() {
        TripOption direct = directOption(35);
        TripOption walking = walkingOnlyOption(36);

        List<TripOption> result = filter.filterDominated(List.of(direct, walking));

        assertThat(result).containsExactlyInAnyOrder(direct, walking);
    }

    @Test
    void keepsNonComparableOptions() {
        TripOption slowFewerTransfers = directOption(40);
        TripOption fastMoreTransfers = oneTransferOption(35);

        List<TripOption> result = filter.filterDominated(List.of(slowFewerTransfers, fastMoreTransfers));

        assertThat(result).containsExactlyInAnyOrder(slowFewerTransfers, fastMoreTransfers);
    }
}
