package biz.ugur.busroutebackend.geospatial.domain.constants;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class TurkmenistanBoundsTest {

    @Nested
    class StandardBounds {

        @Test
        void acceptsAshgabatCoordinates() {
            assertTrue(TurkmenistanBounds.isWithinStandardBounds(37.9601, 58.3261));
            assertTrue(TurkmenistanBounds.isWithinStandardBounds(
                    new BigDecimal("37.9601"), new BigDecimal("58.3261")));
        }

        @Test
        void rejectsOutsidePoint() {
            assertFalse(TurkmenistanBounds.isWithinStandardBounds(0.0, 0.0));
            assertFalse(TurkmenistanBounds.isWithinStandardBounds(90.0, 180.0));
        }

        @Test
        void rejectsNullInputs() {
            assertFalse(TurkmenistanBounds.isWithinStandardBounds((Double) null, 58.0));
            assertFalse(TurkmenistanBounds.isWithinStandardBounds(37.0, null));
            assertFalse(TurkmenistanBounds.isWithinStandardBounds((BigDecimal) null, BigDecimal.ZERO));
            assertFalse(TurkmenistanBounds.isWithinStandardBounds(BigDecimal.ZERO, null));
        }

        @Test
        void boundariesInclusive() {
            assertTrue(TurkmenistanBounds.isWithinStandardBounds(
                    TurkmenistanBounds.MIN_LATITUDE, TurkmenistanBounds.MIN_LONGITUDE));
            assertTrue(TurkmenistanBounds.isWithinStandardBounds(
                    TurkmenistanBounds.MAX_LATITUDE, TurkmenistanBounds.MAX_LONGITUDE));
        }

        @Test
        void validateStandardBoundsPassesInside() {
            assertDoesNotThrow(() -> TurkmenistanBounds.validateStandardBounds(
                    new BigDecimal("37.96"), new BigDecimal("58.32")));
        }

        @Test
        void validateStandardBoundsThrowsOutside() {
            assertThrows(IllegalArgumentException.class,
                    () -> TurkmenistanBounds.validateStandardBounds(BigDecimal.ZERO, BigDecimal.ZERO));
        }
    }

    @Nested
    class StrictBounds {

        @Test
        void acceptsPointInsideStrict() {
            assertTrue(TurkmenistanBounds.isWithinStrictBounds(37.9601, 58.3261));
            assertTrue(TurkmenistanBounds.isWithinStrictBounds(
                    new BigDecimal("37.9601"), new BigDecimal("58.3261")));
        }

        @Test
        void rejectsPointOutsideStrictButInsideStandard() {
            assertFalse(TurkmenistanBounds.isWithinStrictBounds(35.05, 52.55));
        }

        @Test
        void rejectsNullInputs() {
            assertFalse(TurkmenistanBounds.isWithinStrictBounds((Double) null, 58.0));
            assertFalse(TurkmenistanBounds.isWithinStrictBounds(37.0, null));
        }

        @Test
        void validateStrictBoundsPassesInside() {
            assertDoesNotThrow(() -> TurkmenistanBounds.validateStrictBounds(
                    new BigDecimal("37.96"), new BigDecimal("58.32")));
        }

        @Test
        void validateStrictBoundsThrowsOutside() {
            assertThrows(IllegalArgumentException.class,
                    () -> TurkmenistanBounds.validateStrictBounds(BigDecimal.ZERO, BigDecimal.ZERO));
        }
    }

    @Nested
    class CitiesAndHelpers {

        @Test
        void ashgabatCityCoordinatesWithinBounds() {
            assertTrue(TurkmenistanBounds.isWithinStandardBounds(
                    TurkmenistanBounds.Cities.ASHGABAT_LAT,
                    TurkmenistanBounds.Cities.ASHGABAT_LON));
        }

        @Test
        void allCityCoordinatesWithinStandardBounds() {
            assertTrue(TurkmenistanBounds.isWithinStandardBounds(
                    TurkmenistanBounds.Cities.TURKMENABAT_LAT,
                    TurkmenistanBounds.Cities.TURKMENABAT_LON));
            assertTrue(TurkmenistanBounds.isWithinStandardBounds(
                    TurkmenistanBounds.Cities.TURKMENBASHI_LAT,
                    TurkmenistanBounds.Cities.TURKMENBASHI_LON));
            assertTrue(TurkmenistanBounds.isWithinStandardBounds(
                    TurkmenistanBounds.Cities.MARY_LAT,
                    TurkmenistanBounds.Cities.MARY_LON));
            assertTrue(TurkmenistanBounds.isWithinStandardBounds(
                    TurkmenistanBounds.Cities.BALKANABAT_LAT,
                    TurkmenistanBounds.Cities.BALKANABAT_LON));
            assertTrue(TurkmenistanBounds.isWithinStandardBounds(
                    TurkmenistanBounds.Cities.DASHOGUZ_LAT,
                    TurkmenistanBounds.Cities.DASHOGUZ_LON));
        }

        @Test
        void getCenterPointInBounds() {
            BigDecimal[] center = TurkmenistanBounds.getCenterPoint();
            assertEquals(2, center.length);
            assertTrue(TurkmenistanBounds.isWithinStandardBounds(center[0], center[1]));
        }

        @Test
        void getDocumentationContainsKeyInfo() {
            String doc = TurkmenistanBounds.getDocumentation();
            assertTrue(doc.contains("Ashgabat"));
            assertTrue(doc.contains("Turkmenabat"));
            assertTrue(doc.contains("Turkmenbashi"));
        }
    }
}
