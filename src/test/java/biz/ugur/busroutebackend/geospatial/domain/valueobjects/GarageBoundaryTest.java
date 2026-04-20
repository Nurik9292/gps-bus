package biz.ugur.busroutebackend.geospatial.domain.valueobjects;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GarageBoundaryTest {

    private static final Coordinates P1 = Coordinates.of(37.96, 58.32);
    private static final Coordinates P2 = Coordinates.of(37.96, 58.34);
    private static final Coordinates P3 = Coordinates.of(37.98, 58.34);
    private static final Coordinates P4 = Coordinates.of(37.98, 58.32);

    @Nested
    class Construction {

        @Test
        void ofAutoClosesPolygon() {
            GarageBoundary b = GarageBoundary.of(List.of(P1, P2, P3, P4));
            assertEquals(5, b.getPointCount());
            assertEquals(P1, b.getPoints().get(4));
        }

        @Test
        void ofKeepsClosingPointWhenAlreadyClosed() {
            GarageBoundary b = GarageBoundary.of(List.of(P1, P2, P3, P4, P1));
            assertEquals(5, b.getPointCount());
        }

        @Test
        void ofRejectsNullOrEmpty() {
            assertThrows(IllegalArgumentException.class, () -> GarageBoundary.of(null));
            assertThrows(IllegalArgumentException.class, () -> GarageBoundary.of(List.of()));
        }

        @Test
        void ofRejectsFewerThanMinimumPoints() {
            assertThrows(IllegalArgumentException.class,
                    () -> GarageBoundary.of(List.of(P1, P2)));
        }

        @Test
        void fromArrayBuildsClosedPolygon() {
            double[][] coords = {
                    {37.96, 58.32},
                    {37.96, 58.34},
                    {37.98, 58.34},
                    {37.98, 58.32}
            };
            GarageBoundary b = GarageBoundary.fromArray(coords);
            assertEquals(5, b.getPointCount());
        }

        @Test
        void fromArrayRejectsEmpty() {
            assertThrows(IllegalArgumentException.class, () -> GarageBoundary.fromArray(null));
            assertThrows(IllegalArgumentException.class,
                    () -> GarageBoundary.fromArray(new double[][]{{37.96, 58.32}}));
        }

        @Test
        void fromArrayRejectsBadPair() {
            double[][] bad = {{37.96, 58.32}, {37.96, 58.34}, {37.98, 58.34}, {37.98}};
            assertThrows(IllegalArgumentException.class, () -> GarageBoundary.fromArray(bad));
        }
    }

    @Nested
    class Wkt {

        @Test
        void fromWktAcceptsValidPolygon() {
            String wkt = "POLYGON((58.32 37.96, 58.34 37.96, 58.34 37.98, 58.32 37.98, 58.32 37.96))";
            GarageBoundary b = GarageBoundary.fromWKT(wkt);
            assertEquals(5, b.getPointCount());
        }

        @Test
        void fromWktRejectsInvalidFormat() {
            assertThrows(IllegalArgumentException.class,
                    () -> GarageBoundary.fromWKT("LINESTRING(0 0, 1 1)"));
            assertThrows(IllegalArgumentException.class,
                    () -> GarageBoundary.fromWKT(""));
            assertThrows(IllegalArgumentException.class,
                    () -> GarageBoundary.fromWKT(null));
        }

        @Test
        void fromWktRejectsBadCoordinates() {
            String wkt = "POLYGON((58.32 37.96, abc def, 58.34 37.98, 58.32 37.96))";
            assertThrows(IllegalArgumentException.class, () -> GarageBoundary.fromWKT(wkt));
        }

        @Test
        void toWktRoundTrips() {
            GarageBoundary original = GarageBoundary.of(List.of(P1, P2, P3, P4));
            GarageBoundary parsed = GarageBoundary.fromWKT(original.toWKT());
            assertEquals(original.getPointCount(), parsed.getPointCount());
        }
    }

    @Nested
    class Geometry {

        @Test
        void getCentroidIsApproximateMidpoint() {
            GarageBoundary b = GarageBoundary.of(List.of(P1, P2, P3, P4));
            Coordinates center = b.getCentroid();
            assertEquals(37.97, center.getLatitudeAsDouble(), 1e-3);
            assertEquals(58.33, center.getLongitudeAsDouble(), 1e-3);
        }

        @Test
        void isValidWithFourOrMorePoints() {
            GarageBoundary b = GarageBoundary.of(List.of(P1, P2, P3, P4));
            assertTrue(b.isValid());
        }

        @Test
        void toGeoJsonReturnsSinglePolygonRing() {
            GarageBoundary b = GarageBoundary.of(List.of(P1, P2, P3, P4));
            double[][][] geoJson = b.toGeoJson();
            assertEquals(1, geoJson.length);
            assertEquals(5, geoJson[0].length);
            assertEquals(58.32, geoJson[0][0][0], 1e-6);
            assertEquals(37.96, geoJson[0][0][1], 1e-6);
        }

        @Test
        void toStringContainsPointCount() {
            GarageBoundary b = GarageBoundary.of(List.of(P1, P2, P3, P4));
            assertTrue(b.toString().contains("points=5"));
        }
    }
}
