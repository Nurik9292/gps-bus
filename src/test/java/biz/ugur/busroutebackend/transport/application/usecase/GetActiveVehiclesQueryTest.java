package biz.ugur.busroutebackend.transport.application.usecase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GetActiveVehiclesQueryTest {

    @Test
    void allReturnsQueryWithTypeALL() {
        GetActiveVehiclesUseCase.Query q = GetActiveVehiclesUseCase.Query.all();
        assertEquals(GetActiveVehiclesUseCase.Query.QueryType.ALL, q.getType());
        assertEquals("Query{type=ALL}", q.toString());
    }

    @Test
    void byRouteCapturesRouteNumber() {
        GetActiveVehiclesUseCase.Query q = GetActiveVehiclesUseCase.Query.byRoute("29A");
        assertEquals(GetActiveVehiclesUseCase.Query.QueryType.BY_ROUTE, q.getType());
        assertEquals("29A", q.getRouteNumber());
        assertEquals("Query{type=BY_ROUTE, route='29A'}", q.toString());
    }

    @Test
    void byAreaCapturesBounds() {
        GetActiveVehiclesUseCase.Query q = GetActiveVehiclesUseCase.Query.byArea(
                37.95, 58.32, 37.98, 58.35);
        assertEquals(GetActiveVehiclesUseCase.Query.QueryType.BY_AREA, q.getType());
        assertEquals(37.95, q.getMinLat());
        assertEquals(58.32, q.getMinLon());
        assertEquals(37.98, q.getMaxLat());
        assertEquals(58.35, q.getMaxLon());
    }

    @Test
    void byRadiusCapturesCenterAndRadius() {
        GetActiveVehiclesUseCase.Query q = GetActiveVehiclesUseCase.Query.byRadius(
                37.96, 58.33, 500);
        assertEquals(GetActiveVehiclesUseCase.Query.QueryType.BY_RADIUS, q.getType());
        assertEquals(37.96, q.getCenterLat());
        assertEquals(58.33, q.getCenterLon());
        assertEquals(500, q.getRadiusMeters());
    }

    @Test
    void allQueryHasNullArea() {
        GetActiveVehiclesUseCase.Query q = GetActiveVehiclesUseCase.Query.all();
        assertNull(q.getRouteNumber());
        assertNull(q.getMinLat());
        assertNull(q.getCenterLat());
    }
}
