package biz.ugur.busroutebackend.geospatial.domain.valueobjects;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class BoundingBoxTest {

    private static final BoundingBox ASHGABAT_BOUNDS = BoundingBox.of(37.9, 38.0, 58.3, 58.4);
    private static final Coordinates INSIDE = Coordinates.of(37.95, 58.35);
    private static final Coordinates OUTSIDE = Coordinates.of(40.0, 60.0);

    @Nested
    class Validation {

        @Test
        void rejectsNullBounds() {
            assertThrows(IllegalArgumentException.class,
                    () -> BoundingBox.of(null, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ONE));
        }

        @Test
        void rejectsMinLatEqualOrGreaterThanMaxLat() {
            assertThrows(IllegalArgumentException.class,
                    () -> BoundingBox.of(38.0, 37.0, 58.0, 59.0));
            assertThrows(IllegalArgumentException.class,
                    () -> BoundingBox.of(38.0, 38.0, 58.0, 59.0));
        }

        @Test
        void rejectsMinLonEqualOrGreaterThanMaxLon() {
            assertThrows(IllegalArgumentException.class,
                    () -> BoundingBox.of(37.0, 38.0, 59.0, 58.0));
        }

        @Test
        void rejectsLatitudeOutsideGlobalRange() {
            assertThrows(IllegalArgumentException.class,
                    () -> BoundingBox.of(-91.0, 38.0, 58.0, 59.0));
            assertThrows(IllegalArgumentException.class,
                    () -> BoundingBox.of(37.0, 91.0, 58.0, 59.0));
        }

        @Test
        void rejectsLongitudeOutsideGlobalRange() {
            assertThrows(IllegalArgumentException.class,
                    () -> BoundingBox.of(37.0, 38.0, -181.0, 59.0));
            assertThrows(IllegalArgumentException.class,
                    () -> BoundingBox.of(37.0, 38.0, 58.0, 181.0));
        }
    }

    @Nested
    class Contains {

        @Test
        void containsPointInside() {
            assertTrue(ASHGABAT_BOUNDS.contains(INSIDE));
        }

        @Test
        void doesNotContainPointOutside() {
            assertFalse(ASHGABAT_BOUNDS.contains(OUTSIDE));
        }

        @Test
        void containsPointsOnBoundaryInclusive() {
            Coordinates corner = Coordinates.of(37.9, 58.3);
            assertTrue(ASHGABAT_BOUNDS.contains(corner));
        }

        @Test
        void containsBigDecimalOverload() {
            assertTrue(ASHGABAT_BOUNDS.contains(new BigDecimal("37.95"), new BigDecimal("58.35")));
            assertFalse(ASHGABAT_BOUNDS.contains(new BigDecimal("40"), new BigDecimal("60")));
        }

        @Test
        void validateThrowsForPointOutside() {
            assertThrows(IllegalArgumentException.class, () -> ASHGABAT_BOUNDS.validate(OUTSIDE));
            assertDoesNotThrow(() -> ASHGABAT_BOUNDS.validate(INSIDE));
        }
    }

    @Nested
    class BoxRelations {

        @Test
        void intersectsOverlappingBox() {
            BoundingBox other = BoundingBox.of(37.95, 38.05, 58.35, 58.45);
            assertTrue(ASHGABAT_BOUNDS.intersects(other));
            assertTrue(other.intersects(ASHGABAT_BOUNDS));
        }

        @Test
        void intersectsFalseForDisjointBoxes() {
            BoundingBox far = BoundingBox.of(40.0, 41.0, 60.0, 61.0);
            assertFalse(ASHGABAT_BOUNDS.intersects(far));
        }

        @Test
        void containsBoundingBoxTrueForSmallerBoxInside() {
            BoundingBox inner = BoundingBox.of(37.95, 37.98, 58.35, 58.38);
            assertTrue(ASHGABAT_BOUNDS.containsBoundingBox(inner));
            assertFalse(inner.containsBoundingBox(ASHGABAT_BOUNDS));
        }

        @Test
        void unionEncompassesBothBoxes() {
            BoundingBox other = BoundingBox.of(39.0, 40.0, 60.0, 61.0);
            BoundingBox union = ASHGABAT_BOUNDS.union(other);
            assertTrue(union.contains(INSIDE));
            assertTrue(union.contains(Coordinates.of(39.5, 60.5)));
        }
    }

    @Nested
    class Geometry {

        @Test
        void getCenterIsMidpoint() {
            Coordinates center = ASHGABAT_BOUNDS.getCenter();
            assertEquals(37.95, center.getLatitudeAsDouble(), 1e-9);
            assertEquals(58.35, center.getLongitudeAsDouble(), 1e-9);
        }

        @Test
        void widthAndHeightInDegrees() {
            assertEquals(0.1, ASHGABAT_BOUNDS.getWidthDegrees(), 1e-9);
            assertEquals(0.1, ASHGABAT_BOUNDS.getHeightDegrees(), 1e-9);
        }

        @Test
        void approximateKilometersArePositive() {
            assertTrue(ASHGABAT_BOUNDS.getApproximateWidthKm() > 0);
            assertTrue(ASHGABAT_BOUNDS.getApproximateHeightKm() > 0);
            assertTrue(ASHGABAT_BOUNDS.getApproximateAreaKm2() > 0);
        }

        @Test
        void getCornersReturnsFourPoints() {
            Coordinates[] corners = ASHGABAT_BOUNDS.getCorners();
            assertEquals(4, corners.length);
        }

        @Test
        void expandGrowsBoundsInBothDirections() {
            BoundingBox bigger = ASHGABAT_BOUNDS.expand(10.0);
            assertTrue(bigger.containsBoundingBox(ASHGABAT_BOUNDS));
            assertTrue(bigger.getApproximateAreaKm2() > ASHGABAT_BOUNDS.getApproximateAreaKm2());
        }

        @Test
        void fromCenterAndRadiusContainsCenter() {
            Coordinates center = Coordinates.of(37.9601, 58.3261);
            BoundingBox box = BoundingBox.fromCenterAndRadius(center, 5.0);
            assertTrue(box.contains(center));
        }
    }

    @Test
    void toWKTProducesPolygon() {
        String wkt = ASHGABAT_BOUNDS.toWKT();
        assertTrue(wkt.startsWith("POLYGON(("));
        assertTrue(wkt.endsWith("))"));
    }

    @Test
    void toStringIncludesBounds() {
        String s = ASHGABAT_BOUNDS.toString();
        assertTrue(s.contains("37.90"));
        assertTrue(s.contains("58.30"));
    }

    @Test
    void equalityBasedOnCoordinates() {
        BoundingBox a = BoundingBox.of(37.9, 38.0, 58.3, 58.4);
        BoundingBox b = BoundingBox.of(37.9, 38.0, 58.3, 58.4);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
