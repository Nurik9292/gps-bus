package biz.ugur.busroutebackend.routing.domain.specification;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripSearchCriteria;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class TripOptionSpecificationsTest {

    private static final Coordinates A = Coordinates.of(37.9601, 58.3261);
    private static final Coordinates B = Coordinates.of(37.9701, 58.3361);
    private static final LocalDateTime DEPART = LocalDateTime.of(2026, 5, 1, 10, 0);

    private TripOption direct(int minutes) {
        return new TripOption(TripType.DIRECT,
                List.of(RouteSegment.busRideSegment(A, B, minutes, "29A")),
                0, DEPART);
    }

    @Test
    void hasAcceptableTransfersMatchesCriteria() {
        Predicate<TripOption> spec = TripOptionSpecifications.hasAcceptableTransfers(
                TripSearchCriteria.fewestTransfers());
        assertTrue(spec.test(direct(10)));

        TripOption twoTransfers = new TripOption(TripType.TWO_TRANSFER,
                List.of(
                        RouteSegment.busRideSegment(A, A, 5, "1"),
                        RouteSegment.transferSegment(A, 1),
                        RouteSegment.busRideSegment(A, A, 5, "2"),
                        RouteSegment.transferSegment(A, 1),
                        RouteSegment.busRideSegment(A, B, 5, "3")
                ),
                0, DEPART);
        assertFalse(spec.test(twoTransfers));
    }

    @Test
    void hasAcceptableTotalTimeCapsAt240() {
        Predicate<TripOption> spec = TripOptionSpecifications.hasAcceptableTotalTime();
        assertTrue(spec.test(direct(100)));
        assertFalse(spec.test(direct(300)));
    }

    @Test
    void isAccessibleForwardsToTripOption() {
        Predicate<TripOption> spec = TripOptionSpecifications.isAccessible();
        assertTrue(spec.test(direct(10)));
    }

    @Test
    void isAcceptableCombinesAllPredicates() {
        Predicate<TripOption> spec = TripOptionSpecifications.isAcceptable(
                TripSearchCriteria.defaultCriteria());
        assertTrue(spec.test(direct(60)));
        assertFalse(spec.test(direct(300)));
    }

    @Test
    void hasAcceptableWalkingTimeDerivedFromDistance() {
        Predicate<TripOption> spec = TripOptionSpecifications.hasAcceptableWalkingTime(
                TripSearchCriteria.defaultCriteria());
        assertTrue(spec.test(direct(20)));
    }

    @Test
    void hasMinimumReliabilityAcceptsOptionAboveThreshold() {
        Predicate<TripOption> lenient = TripOptionSpecifications.hasMinimumReliability(0.5);
        Predicate<TripOption> strict = TripOptionSpecifications.hasMinimumReliability(0.99);
        TripOption option = direct(10);
        assertTrue(lenient.test(option));
        assertFalse(strict.test(option));
    }
}
