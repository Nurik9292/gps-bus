package biz.ugur.busroutebackend.routing.domain.valueobjects;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.domain.enums.SegmentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteSegmentTest {

    private static final Coordinates A = Coordinates.of(37.9601, 58.3261);
    private static final Coordinates B = Coordinates.of(37.9701, 58.3361);

    @Test
    void walkingSegmentFactorySetsTypeAndInstruction() {
        RouteSegment s = RouteSegment.walkingSegment(A, B, 5);
        assertEquals(SegmentType.WALKING, s.getType());
        assertEquals(5, s.getDurationMinutes());
        assertNull(s.getRouteNumber());
        assertTrue(s.getInstruction().contains("Walk"));
        assertTrue(s.getDetailedDescription().contains("Walk"));
    }

    @Test
    void busRideSegmentStoresRouteNumber() {
        RouteSegment s = RouteSegment.busRideSegment(A, B, 12, "29A");
        assertEquals(SegmentType.BUS_RIDE, s.getType());
        assertEquals("29A", s.getRouteNumber());
        assertTrue(s.getInstruction().contains("29A"));
        assertTrue(s.getDetailedDescription().contains("29A"));
    }

    @Test
    void transferSegmentHasSameFromAndToLocation() {
        RouteSegment s = RouteSegment.transferSegment(A, 4);
        assertEquals(SegmentType.TRANSFER, s.getType());
        assertEquals(A, s.getFromLocation());
        assertEquals(A, s.getToLocation());
        assertEquals(4, s.getDurationMinutes());
        assertEquals("Transfer", s.getDetailedDescription());
    }

    @Test
    void busRideSegmentWithGeometryKeepsWktAndDistance() {
        String wkt = "LINESTRING(58.3261 37.9601, 58.3361 37.9701)";
        RouteSegment s = RouteSegment.busRideSegmentWithGeometry(A, B, 10, "29A", wkt, 1500);
        assertEquals(wkt, s.getRouteGeometryWkt());
        assertEquals(1500, s.getTotalDistanceMeters());
    }

    @Test
    void walkingSegmentWithGeometryKeepsGeometryAndDistance() {
        List<List<Double>> geometry = List.of(
                List.of(37.9601, 58.3261),
                List.of(37.9701, 58.3361));
        RouteSegment s = RouteSegment.walkingSegmentWithGeometry(A, B, 7, geometry, 420);
        assertEquals(geometry, s.getWalkingGeometry());
        assertEquals(420, s.getTotalDistanceMeters());
    }

    @Test
    void getGeoJsonCoordinatesParsesWktLineString() {
        String wkt = "LINESTRING(58.3261 37.9601, 58.3361 37.9701)";
        RouteSegment s = RouteSegment.busRideSegmentWithGeometry(A, B, 10, "29A", wkt, 1500);
        List<Double[]> coords = s.getGeoJsonCoordinates();
        assertEquals(2, coords.size());
        assertEquals(37.9601, coords.get(0)[0], 1e-6);
        assertEquals(58.3261, coords.get(0)[1], 1e-6);
    }

    @Test
    void getGeoJsonCoordinatesReturnsNullWhenWktMissing() {
        RouteSegment s = RouteSegment.walkingSegment(A, B, 5);
        assertNull(s.getGeoJsonCoordinates());
    }

    @Test
    void toStringIncludesDurationAndType() {
        RouteSegment s = RouteSegment.walkingSegment(A, B, 5);
        assertTrue(s.toString().contains("WALKING"));
        assertTrue(s.toString().contains("5"));
    }
}
