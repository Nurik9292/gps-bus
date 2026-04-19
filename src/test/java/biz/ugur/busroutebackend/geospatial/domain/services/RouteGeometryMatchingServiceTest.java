package biz.ugur.busroutebackend.geospatial.domain.services;

import biz.ugur.busroutebackend.geospatial.domain.services.RouteGeometryMatchingService.GpsPoint;
import biz.ugur.busroutebackend.geospatial.domain.services.RouteGeometryMatchingService.RouteMatchResult;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteGeometry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteGeometryMatchingServiceTest {

    private RouteGeometryMatchingService service;
    private BusRoute route;

    @BeforeEach
    void setUp() {
        service = new RouteGeometryMatchingService(new DistanceCalculationService(), 500.0, 1000.0);

        RouteGeometry fwd = new RouteGeometry(List.of(
                Coordinates.of(37.9601, 58.3261),
                Coordinates.of(37.9701, 58.3361)));
        RouteGeometry bwd = new RouteGeometry(List.of(
                Coordinates.of(37.9701, 58.3361),
                Coordinates.of(37.9601, 58.3261)));
        route = BusRoute.create("29A", "Name", "", "", "#FF5722", "ashgabat", 30)
                .updateRouteGeometry(fwd, bwd);
    }

    @Test
    void noMatchWhenGpsPointsEmpty() {
        RouteMatchResult result = service.calculateMatchScore(List.of(), route);
        assertFalse(result.isMatch());
        assertNotNull(result.reason());
    }

    @Test
    void noMatchWhenRouteIsNull() {
        GpsPoint p = new GpsPoint(37.96, 58.33, 30.0, 1L);
        RouteMatchResult result = service.calculateMatchScore(List.of(p), null);
        assertFalse(result.isMatch());
    }

    @Test
    void noMatchReportsReasonForRouteWithoutGeometry() {
        BusRoute empty = BusRoute.create("1", "Name", "", "", "#FF0000", "city", 10);
        GpsPoint p = new GpsPoint(37.96, 58.33, 30.0, 1L);
        RouteMatchResult result = service.calculateMatchScore(List.of(p), empty);
        assertEquals("Route has no geometry", result.reason());
    }

    @Test
    void noMatchFactoryProducesZeroConfidenceEmptyFields() {
        RouteMatchResult r = RouteMatchResult.noMatch("nope");
        assertNull(r.routeId());
        assertNull(r.routeNumber());
        assertEquals(0, r.confidence());
        assertFalse(r.isMatch());
        assertEquals("nope", r.reason());
    }

    @Test
    void matchScoreReturnsConfidenceBetween0And100() {
        List<GpsPoint> pts = List.of(
                new GpsPoint(37.9601, 58.3261, 20.0, 1L),
                new GpsPoint(37.9651, 58.3311, 25.0, 2L),
                new GpsPoint(37.9701, 58.3361, 22.0, 3L)
        );
        RouteMatchResult result = service.calculateMatchScore(pts, route);
        assertNotNull(result);
        assertTrue(result.confidence() >= 0);
        assertTrue(result.confidence() <= 100);
        assertNotNull(result.direction());
        assertTrue(result.direction().equals("forward") || result.direction().equals("backward"));
    }

    @Test
    void isDeviatingReturnsFalseWhenNoGps() {
        assertFalse(service.isDeviatingFromRoute(List.of(), route));
        assertFalse(service.isDeviatingFromRoute(null, route));
    }

    @Test
    void isDeviatingReturnsFalseForRouteWithoutGeometry() {
        BusRoute empty = BusRoute.create("1", "Name", "", "", "#FF0000", "city", 10);
        List<GpsPoint> pts = List.of(new GpsPoint(37.96, 58.33, 0.0, 1L));
        assertFalse(service.isDeviatingFromRoute(pts, empty));
    }

    @Test
    void calculateAverageDistanceReturnsMaxWhenRouteMissing() {
        List<GpsPoint> pts = List.of(new GpsPoint(37.96, 58.33, 0.0, 1L));
        BusRoute empty = BusRoute.create("1", "Name", "", "", "#FF0000", "city", 10);
        double avg = service.calculateAverageDistanceFromRoute(pts, empty);
        assertEquals(Double.MAX_VALUE, avg);
    }

    @Test
    void calculateAverageDistanceReturnsFiniteValueForValidInputs() {
        List<GpsPoint> pts = List.of(
                new GpsPoint(37.9601, 58.3261, 20.0, 1L),
                new GpsPoint(37.9651, 58.3311, 25.0, 2L)
        );
        double avg = service.calculateAverageDistanceFromRoute(pts, route);
        assertTrue(avg >= 0);
        assertNotEquals(Double.MAX_VALUE, avg);
    }

    @Test
    void calculateAverageDistanceReturnsMaxForEmptyGps() {
        double avg = service.calculateAverageDistanceFromRoute(List.of(), route);
        assertEquals(Double.MAX_VALUE, avg);
    }
}
