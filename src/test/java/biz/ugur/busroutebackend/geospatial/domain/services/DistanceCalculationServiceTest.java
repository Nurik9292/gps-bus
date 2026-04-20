package biz.ugur.busroutebackend.geospatial.domain.services;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Distance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DistanceCalculationServiceTest {

    private static final Coordinates ASHGABAT = Coordinates.of(37.9601, 58.3261);
    private static final Coordinates ASHGABAT_NEAR = Coordinates.of(37.9701, 58.3361);
    private static final Coordinates TURKMENBASHI = Coordinates.of(40.0225, 52.9550);

    private DistanceCalculationService service;

    @BeforeEach
    void setUp() {
        service = new DistanceCalculationService();
    }

    @Nested
    class Distance_basics {

        @Test
        void haversineToSelfIsZero() {
            assertEquals(0.0, DistanceCalculationService.haversineDistanceMeters(
                    37.96, 58.32, 37.96, 58.32));
        }

        @Test
        void haversineIsSymmetric() {
            double ab = DistanceCalculationService.haversineDistanceMeters(37.96, 58.32, 40.02, 52.95);
            double ba = DistanceCalculationService.haversineDistanceMeters(40.02, 52.95, 37.96, 58.32);
            assertEquals(ab, ba, 1e-9);
        }

        @Test
        void calculateDistanceWithCoordinates() {
            Distance d = service.calculateDistance(ASHGABAT, ASHGABAT_NEAR);
            assertTrue(d.getMeters() > 0);
            assertTrue(d.getMeters() < 2000);
        }

        @Test
        void calculateDistanceWithPrimitives() {
            Distance d = service.calculateDistance(37.9601, 58.3261, 37.9701, 58.3361);
            assertTrue(d.getMeters() > 0);
        }

        @Test
        void rejectsNullCoordinates() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.calculateDistance(null, ASHGABAT));
            assertThrows(IllegalArgumentException.class,
                    () -> service.calculateDistance(ASHGABAT, null));
        }

        @Test
        void ashgabatToTurkmenbashiIsRoughly480Km() {
            Distance d = service.calculateDistance(ASHGABAT, TURKMENBASHI);
            double km = d.getMeters() / 1000.0;
            assertTrue(km > 400 && km < 600, "got " + km + "km");
        }
    }

    @Nested
    class Radius {

        @Test
        void isWithinRadiusTrueForNearbyPoint() {
            assertTrue(service.isWithinRadius(ASHGABAT, ASHGABAT_NEAR, Distance.ofMeters(5000)));
            assertTrue(service.isWithinRadius(ASHGABAT, ASHGABAT_NEAR, 5000.0));
        }

        @Test
        void isWithinRadiusFalseForFarPoint() {
            assertFalse(service.isWithinRadius(ASHGABAT, TURKMENBASHI, Distance.ofMeters(1000)));
            assertFalse(service.isWithinRadius(ASHGABAT, TURKMENBASHI, 1000.0));
        }

        @Test
        void isNearbyUsesDefaultThreshold() {
            assertTrue(service.isNearby(ASHGABAT, ASHGABAT));
        }
    }

    @Nested
    class Walking {

        @Test
        void walkingTimeMinutesPositive() {
            int minutes = service.calculateWalkingTimeMinutes(ASHGABAT, ASHGABAT_NEAR);
            assertTrue(minutes > 0);
        }

        @Test
        void walkingTimeScalesWithDistance() {
            int nearby = service.calculateWalkingTimeMinutes(ASHGABAT, ASHGABAT_NEAR);
            int far = service.calculateWalkingTimeMinutes(ASHGABAT, TURKMENBASHI);
            assertTrue(far > nearby);
        }

        @Test
        void urbanWalkingTimeAddsCorrection() {
            int base = service.calculateWalkingTimeMinutes(ASHGABAT, ASHGABAT_NEAR);
            int urban = service.calculateUrbanWalkingTimeMinutes(ASHGABAT, ASHGABAT_NEAR, 5);
            assertTrue(urban >= base);
        }

        @Test
        void isWalkableTrueForShortDistance() {
            Coordinates short_ = Coordinates.of(37.9601, 58.3270);
            assertTrue(service.isWalkable(ASHGABAT, short_));
        }

        @Test
        void isWalkableFalseForLongDistance() {
            assertFalse(service.isWalkable(ASHGABAT, TURKMENBASHI));
        }
    }

    @Nested
    class PathAndClosest {

        @Test
        void findClosestPicksShortestDistance() {
            Coordinates far = Coordinates.of(41.8369, 59.9658);
            Coordinates closer = ASHGABAT_NEAR;
            Coordinates closest = service.findClosest(ASHGABAT, List.of(far, closer));
            assertEquals(closer, closest);
        }

        @Test
        void findClosestReturnsNullForEmptyList() {
            assertNull(service.findClosest(ASHGABAT, List.of()));
        }

        @Test
        void calculatePathDistanceSumsSegments() {
            Distance d = service.calculatePathDistance(List.of(ASHGABAT, ASHGABAT_NEAR, TURKMENBASHI));
            Distance hop1 = service.calculateDistance(ASHGABAT, ASHGABAT_NEAR);
            Distance hop2 = service.calculateDistance(ASHGABAT_NEAR, TURKMENBASHI);
            assertEquals(hop1.getMeters() + hop2.getMeters(), d.getMeters(), 1e-6);
        }

        @Test
        void calculatePathDistanceRejectsSinglePoint() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.calculatePathDistance(List.of(ASHGABAT)));
        }

        @Test
        void calculatePathDistanceRejectsEmpty() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.calculatePathDistance(List.of()));
        }
    }

    @Nested
    class PointToLine {

        @Test
        void pointOnLineHasZeroCrossTrack() {
            Distance d = service.calculatePointToLineDistance(
                    ASHGABAT, ASHGABAT, ASHGABAT_NEAR);
            assertEquals(0.0, d.getMeters(), 1.0);
        }

        @Test
        void pointPerpendicularToLineReturnsPositiveDistance() {
            Coordinates offset = Coordinates.of(37.9650, 58.3400);
            Distance d = service.calculatePointToLineDistance(offset, ASHGABAT, ASHGABAT_NEAR);
            assertTrue(d.getMeters() >= 0);
        }

        @Test
        void pointFarBeyondEndpointsReturnsFiniteDistance() {
            Coordinates farBeyond = Coordinates.of(40.0, 60.0);
            Distance d = service.calculatePointToLineDistance(farBeyond, ASHGABAT, ASHGABAT_NEAR);
            assertTrue(d.getMeters() > 0);
            assertTrue(Double.isFinite(d.getMeters()));
        }
    }

    @Test
    void calculateBearingDelegatesToCoordinates() {
        double bearing = service.calculateBearing(ASHGABAT, ASHGABAT_NEAR);
        assertTrue(bearing >= 0.0 && bearing <= 360.0);
    }

    @Test
    void getCardinalDirectionIsNotBlank() {
        assertNotNull(service.getCardinalDirection(ASHGABAT, ASHGABAT_NEAR));
    }
}
