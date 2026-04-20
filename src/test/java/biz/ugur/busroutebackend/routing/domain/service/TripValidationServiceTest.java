package biz.ugur.busroutebackend.routing.domain.service;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.domain.exceptions.InvalidLocationException;
import biz.ugur.busroutebackend.routing.domain.exceptions.TripPlanningException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TripValidationServiceTest {

    private static final Coordinates ASHGABAT = Coordinates.of(37.9601, 58.3261);
    private static final Coordinates NEARBY = Coordinates.of(37.9701, 58.3361);
    private static final Coordinates FAR_INSIDE_TKM = Coordinates.of(41.8369, 59.9658);
    private static final Coordinates OUT_OF_BOUNDS = Coordinates.of(0.0, 0.0);

    private TripValidationService service;

    @BeforeEach
    void setUp() {
        service = new TripValidationService(new DistanceCalculationService());
    }

    @Test
    void validateTripLocationsAcceptsInBoundsOriginAndDestination() {
        assertDoesNotThrow(() -> service.validateTripLocations(ASHGABAT, NEARBY));
    }

    @Test
    void validateLocationWithinBoundsRejectsNull() {
        InvalidLocationException ex = assertThrows(InvalidLocationException.class,
                () -> service.validateLocationWithinBounds(null, "Origin"));
        assertTrue(ex.getMessage().contains("Origin"));
    }

    @Test
    void validateLocationWithinBoundsRejectsOutOfTurkmenistan() {
        assertThrows(TripPlanningException.class,
                () -> service.validateLocationWithinBounds(OUT_OF_BOUNDS, "Origin"));
    }

    @Test
    void validateTripDistanceRejectsTooLongTrip() {
        Coordinates origin = Coordinates.of(37.9601, 58.3261);
        Coordinates farAway = Coordinates.of(40.0225, 52.9550);
        TripPlanningException ex = assertThrows(TripPlanningException.class,
                () -> service.validateTripDistance(origin, farAway));
        assertNotNull(ex);
    }

    @Test
    void isTripWalkableReturnsTrueForShortDistance() {
        assertTrue(service.isTripWalkable(ASHGABAT, NEARBY, 5000));
    }

    @Test
    void isTripWalkableReturnsFalseWhenExceedsBudget() {
        assertFalse(service.isTripWalkable(ASHGABAT, FAR_INSIDE_TKM, 500));
    }

    @Test
    void calculateWalkingTimeMinutesIsPositive() {
        int minutes = service.calculateWalkingTimeMinutes(ASHGABAT, NEARBY);
        assertTrue(minutes > 0);
    }

    @Test
    void calculateWalkingTimeMinutesScalesWithDistance() {
        int short_ = service.calculateWalkingTimeMinutes(ASHGABAT, NEARBY);
        int long_ = service.calculateWalkingTimeMinutes(ASHGABAT, FAR_INSIDE_TKM);
        assertTrue(long_ > short_);
    }
}
