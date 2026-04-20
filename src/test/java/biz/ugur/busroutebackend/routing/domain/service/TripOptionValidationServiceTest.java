package biz.ugur.busroutebackend.routing.domain.service;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripSearchCriteria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TripOptionValidationServiceTest {

    private static final Coordinates ORIGIN = Coordinates.of(37.9601, 58.3261);
    private static final Coordinates HUB = Coordinates.of(37.9651, 58.3311);
    private static final Coordinates DEST = Coordinates.of(37.9701, 58.3361);
    private static final LocalDateTime DEPART = LocalDateTime.of(2026, 5, 1, 10, 0);

    private TripOptionValidationService service;

    @BeforeEach
    void setUp() {
        service = new TripOptionValidationService(new DistanceCalculationService());
    }

    private TripOption direct(int minutes) {
        return new TripOption(TripType.DIRECT,
                List.of(RouteSegment.busRideSegment(ORIGIN, DEST, minutes, "29A")),
                0, DEPART);
    }

    private TripOption withTransfers(int n, int minutesEach) {
        List<RouteSegment> segments = new java.util.ArrayList<>();
        segments.add(RouteSegment.busRideSegment(ORIGIN, HUB, minutesEach, "1"));
        for (int i = 0; i < n; i++) {
            segments.add(RouteSegment.transferSegment(HUB, 1));
            segments.add(RouteSegment.busRideSegment(HUB, i == n - 1 ? DEST : HUB, minutesEach, String.valueOf(i + 2)));
        }
        return new TripOption(n == 1 ? TripType.ONE_TRANSFER : TripType.TWO_TRANSFER,
                segments, 0, DEPART);
    }

    @Test
    void acceptableWhenWithinCriteria() {
        assertTrue(service.isOptionAcceptable(direct(20), TripSearchCriteria.defaultCriteria()));
    }

    @Test
    void rejectsTooManyTransfers() {
        TripSearchCriteria strict = TripSearchCriteria.fewestTransfers();
        TripOption threeTransfers = new TripOption(TripType.TWO_TRANSFER,
                List.of(
                        RouteSegment.busRideSegment(ORIGIN, HUB, 5, "1"),
                        RouteSegment.transferSegment(HUB, 1),
                        RouteSegment.busRideSegment(HUB, HUB, 5, "2"),
                        RouteSegment.transferSegment(HUB, 1),
                        RouteSegment.busRideSegment(HUB, DEST, 5, "3")
                ),
                0, DEPART);
        assertFalse(service.isOptionAcceptable(threeTransfers, strict));
    }

    @Test
    void rejectsOptionsLongerThanMax240Minutes() {
        TripOption veryLong = direct(300);
        assertFalse(service.isOptionAcceptable(veryLong, TripSearchCriteria.defaultCriteria()));
    }

    @Test
    void isValidForTripTrueWhenCloseToOriginAndDestination() {
        assertTrue(service.isValidForTrip(direct(10), ORIGIN, DEST));
    }

    @Test
    void isValidForTripFalseWhenOriginFarFromFirstSegment() {
        Coordinates farOrigin = Coordinates.of(40.5, 60.0);
        assertFalse(service.isValidForTrip(direct(10), farOrigin, DEST));
    }

    @Test
    void isValidForTripFalseWhenDestinationFarFromLastSegment() {
        Coordinates farDest = Coordinates.of(41.0, 60.0);
        assertFalse(service.isValidForTrip(direct(10), ORIGIN, farDest));
    }
}
