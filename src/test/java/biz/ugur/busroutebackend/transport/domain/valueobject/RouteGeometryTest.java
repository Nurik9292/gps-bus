package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteGeometryTest {

    private static final Coordinates A = Coordinates.of(37.9601, 58.3261);
    private static final Coordinates B = Coordinates.of(37.9701, 58.3361);
    private static final Coordinates C = Coordinates.of(37.9801, 58.3461);

    @Test
    void constructorRejectsEmptyList() {
        assertThrows(IllegalArgumentException.class,
                () -> new RouteGeometry(List.of()));
    }

    @Test
    void constructorRejectsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> new RouteGeometry(null));
    }

    @Test
    void constructorRejectsSinglePointBecauseLineStringNeedsTwoOrMore() {
        assertThrows(IllegalArgumentException.class,
                () -> new RouteGeometry(List.of(A)));
    }

    @Test
    void getPointCountReflectsList() {
        RouteGeometry g = new RouteGeometry(List.of(A, B, C));
        assertEquals(3, g.getPointCount());
    }

    @Test
    void isValidTrueForLineString() {
        assertTrue(new RouteGeometry(List.of(A, B)).isValid());
    }

    @Test
    void toWktRoundtripPreservesPointCount() {
        RouteGeometry g = new RouteGeometry(List.of(A, B, C));
        String wkt = g.toWKT();
        assertTrue(wkt.startsWith("LINESTRING("));
        RouteGeometry back = RouteGeometry.fromWKT(wkt);
        assertEquals(3, back.getPointCount());
    }

    @Test
    void fromWktRejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> RouteGeometry.fromWKT("  "));
        assertThrows(IllegalArgumentException.class, () -> RouteGeometry.fromWKT(null));
    }

    @Test
    void fromWktRejectsWrongPrefix() {
        assertThrows(IllegalArgumentException.class,
                () -> RouteGeometry.fromWKT("POINT(58 37)"));
    }

    @Test
    void fromWktRejectsEmptyCoordinates() {
        assertThrows(IllegalArgumentException.class,
                () -> RouteGeometry.fromWKT("LINESTRING( )"));
    }

    @Test
    void fromWktRejectsMalformedPoint() {
        assertThrows(IllegalArgumentException.class,
                () -> RouteGeometry.fromWKT("LINESTRING(58 37, only-one-coord)"));
    }

    @Test
    void fromCoordinatesRejectsEmpty() {
        assertThrows(IllegalArgumentException.class,
                () -> RouteGeometry.fromCoordinates(List.of()));
    }

    @Test
    void fromCoordinatesRejectsBadPoint() {
        assertThrows(IllegalArgumentException.class,
                () -> RouteGeometry.fromCoordinates(List.of(List.of(37.0))));
    }

    @Test
    void fromCoordinateArraysRejectsBadPoint() {
        List<Double[]> bad = new java.util.ArrayList<>();
        bad.add(new Double[]{37.0});
        assertThrows(IllegalArgumentException.class,
                () -> RouteGeometry.fromCoordinateArrays(bad));
    }

    @Test
    void toCoordinatesRoundtripsLatLon() {
        RouteGeometry g = new RouteGeometry(List.of(A, B));
        List<List<Double>> coords = g.toCoordinates();
        assertEquals(2, coords.size());
        assertEquals(37.9601, coords.get(0).get(0), 1e-6);
        assertEquals(58.3261, coords.get(0).get(1), 1e-6);
    }

    @Test
    void toCoordinateArraysReturnsSameData() {
        RouteGeometry g = new RouteGeometry(List.of(A, B));
        List<Double[]> arrays = g.toCoordinateArrays();
        assertEquals(2, arrays.size());
        assertEquals(37.9601, arrays.get(0)[0], 1e-6);
    }

    @Test
    void reverseSwapsOrder() {
        RouteGeometry g = new RouteGeometry(List.of(A, B, C));
        RouteGeometry r = g.reverse();
        assertEquals(3, r.getPointCount());
        assertEquals(C, r.getPoints().get(0));
        assertEquals(A, r.getPoints().get(2));
    }

    @Test
    void calculateDistanceMetersSimpleIsPositive() {
        RouteGeometry g = new RouteGeometry(List.of(A, B));
        assertTrue(g.calculateDistanceMetersSimple() > 0);
    }

    @Test
    void toStringContainsPointCount() {
        RouteGeometry g = new RouteGeometry(List.of(A, B, C));
        assertTrue(g.toString().contains("points=3"));
    }
}
