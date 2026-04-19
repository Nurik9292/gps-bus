package biz.ugur.busroutebackend.geospatial.domain.valueobjects;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DistanceTest {

    @Nested
    class Construction {

        @Test
        void ofMetersAcceptsZero() {
            assertEquals(0.0, Distance.zero().getMeters());
            assertEquals(0.0, Distance.ofMeters(0.0).getMeters());
        }

        @Test
        void rejectsNegativeMeters() {
            assertThrows(IllegalArgumentException.class, () -> Distance.ofMeters(-1));
        }

        @Test
        void rejectsNaN() {
            assertThrows(IllegalArgumentException.class, () -> Distance.ofMeters(Double.NaN));
        }

        @Test
        void rejectsInfinite() {
            assertThrows(IllegalArgumentException.class, () -> Distance.ofMeters(Double.POSITIVE_INFINITY));
        }

        @Test
        void ofKilometersConverts() {
            assertEquals(1000.0, Distance.ofKilometers(1.0).getMeters(), 1e-6);
            assertEquals(2500.0, Distance.ofKilometers(2.5).getMeters(), 1e-6);
        }

        @Test
        void ofMilesConverts() {
            double oneMileMeters = Distance.ofMiles(1.0).getMeters();
            assertTrue(oneMileMeters > 1600 && oneMileMeters < 1610);
        }
    }

    @Nested
    class Conversions {

        @Test
        void toKilometers() {
            assertEquals(2.5, Distance.ofMeters(2500).toKilometers(), 1e-9);
        }

        @Test
        void toCentimeters() {
            assertEquals(500.0, Distance.ofMeters(5).toCentimeters(), 1e-9);
        }

        @Test
        void toFeetApprox() {
            assertEquals(328.084, Distance.ofMeters(100).toFeet(), 1e-3);
        }

        @Test
        void toMetersIntRoundsHalfUp() {
            assertEquals(5, Distance.ofMeters(5.4).toMetersInt());
            assertEquals(6, Distance.ofMeters(5.6).toMetersInt());
        }
    }

    @Nested
    class Comparisons {

        @Test
        void isLessThanAndGreaterThan() {
            Distance a = Distance.ofMeters(100);
            Distance b = Distance.ofMeters(200);
            assertTrue(a.isLessThan(b));
            assertTrue(b.isGreaterThan(a));
            assertFalse(a.isGreaterThan(b));
        }

        @Test
        void lessThanOrEqualInclusive() {
            Distance a = Distance.ofMeters(100);
            assertTrue(a.isLessThanOrEqual(Distance.ofMeters(100)));
            assertTrue(a.isGreaterThanOrEqual(Distance.ofMeters(100)));
        }

        @Test
        void compareToAlignsWithMeters() {
            Distance a = Distance.ofMeters(10);
            Distance b = Distance.ofMeters(20);
            assertTrue(a.compareTo(b) < 0);
            assertEquals(0, a.compareTo(Distance.ofMeters(10)));
        }

        @Test
        void isNegligibleForLessThanMeter() {
            assertTrue(Distance.ofMeters(0.5).isNegligible());
            assertFalse(Distance.ofMeters(1.0).isNegligible());
        }

        @Test
        void isWithinRadius() {
            Distance inside = Distance.ofMeters(100);
            assertTrue(inside.isWithinRadius(Distance.ofMeters(500)));
            assertFalse(Distance.ofMeters(600).isWithinRadius(Distance.ofMeters(500)));
        }
    }

    @Nested
    class Arithmetic {

        @Test
        void addSumsMeters() {
            assertEquals(300.0, Distance.ofMeters(100).add(Distance.ofMeters(200)).getMeters());
        }

        @Test
        void subtractCannotGoNegative() {
            assertThrows(IllegalArgumentException.class,
                    () -> Distance.ofMeters(100).subtract(Distance.ofMeters(200)));
        }

        @Test
        void subtractNonNegative() {
            assertEquals(50.0, Distance.ofMeters(100).subtract(Distance.ofMeters(50)).getMeters());
        }

        @Test
        void multiplyByNonNegativeFactor() {
            assertEquals(500.0, Distance.ofMeters(100).multiply(5).getMeters());
            assertEquals(0.0, Distance.ofMeters(100).multiply(0).getMeters());
        }

        @Test
        void multiplyRejectsNegativeFactor() {
            assertThrows(IllegalArgumentException.class,
                    () -> Distance.ofMeters(100).multiply(-1));
        }

        @Test
        void divideByPositive() {
            assertEquals(50.0, Distance.ofMeters(100).divide(2).getMeters());
        }

        @Test
        void divideRejectsZeroAndNegative() {
            Distance d = Distance.ofMeters(100);
            assertThrows(IllegalArgumentException.class, () -> d.divide(0));
            assertThrows(IllegalArgumentException.class, () -> d.divide(-1));
        }

        @Test
        void minPicksSmaller() {
            Distance a = Distance.ofMeters(100);
            Distance b = Distance.ofMeters(200);
            assertSame(a, a.min(b));
            assertSame(a, b.min(a));
        }

        @Test
        void maxPicksLarger() {
            Distance a = Distance.ofMeters(100);
            Distance b = Distance.ofMeters(200);
            assertSame(b, a.max(b));
            assertSame(b, b.max(a));
        }
    }

    @Nested
    class WalkingAndTime {

        @Test
        void isWalkableDefaultThreshold() {
            assertTrue(Distance.ofMeters(500).isWalkable());
            assertFalse(Distance.ofMeters(5000).isWalkable());
        }

        @Test
        void walkingTimeMinutesPositive() {
            assertTrue(Distance.ofMeters(1000).toWalkingTimeMinutes() > 0);
        }

        @Test
        void walkingTimeScalesWithSpeed() {
            int slower = Distance.ofMeters(5000).toWalkingTimeMinutes(3.0);
            int faster = Distance.ofMeters(5000).toWalkingTimeMinutes(6.0);
            assertTrue(slower > faster);
        }

        @Test
        void travelTimeByBusMinutes() {
            int minutes = Distance.ofMeters(10_000).toTravelTimeMinutes(30.0);
            assertTrue(minutes > 0);
            assertTrue(minutes <= 60);
        }
    }

    @Nested
    class Formatting {

        @Test
        void shortDistancesRenderedInMeters() {
            assertEquals("500m", Distance.ofMeters(500).toString());
        }

        @Test
        void mediumDistancesRenderedInKm() {
            assertTrue(Distance.ofMeters(5000).toString().contains("km"));
        }

        @Test
        void toFormattedStringUsesDecimalPlaces() {
            assertEquals("500.00m", Distance.ofMeters(500).toFormattedString(2));
        }

        @Test
        void toStringWithWalkingTime() {
            assertTrue(Distance.ofMeters(500).toStringWithWalkingTime().contains("min walk"));
        }

        @Test
        void toMilesStringAdjustsByMagnitude() {
            assertTrue(Distance.ofMeters(50).toMilesString().contains("ft"));
            assertTrue(Distance.ofKilometers(5).toMilesString().contains("mi"));
        }
    }

    @Nested
    class Statistics {

        @Test
        void sumAllDistances() {
            Distance total = Distance.sum(Distance.ofMeters(100), Distance.ofMeters(200), Distance.ofMeters(300));
            assertEquals(600.0, total.getMeters());
        }

        @Test
        void staticMinAndMax() {
            Distance a = Distance.ofMeters(100);
            Distance b = Distance.ofMeters(300);
            Distance c = Distance.ofMeters(200);
            assertSame(a, Distance.min(a, b, c));
            assertSame(b, Distance.max(a, b, c));
        }

        @Test
        void staticAverage() {
            Distance avg = Distance.average(Distance.ofMeters(100), Distance.ofMeters(200), Distance.ofMeters(300));
            assertEquals(200.0, avg.getMeters(), 1e-9);
        }

        @Test
        void staticReducerRejectsEmptyInput() {
            assertThrows(IllegalArgumentException.class, Distance::min);
            assertThrows(IllegalArgumentException.class, Distance::max);
            assertThrows(IllegalArgumentException.class, Distance::average);
        }
    }
}
