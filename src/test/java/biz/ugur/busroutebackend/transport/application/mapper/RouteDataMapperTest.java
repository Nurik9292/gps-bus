package biz.ugur.busroutebackend.transport.application.mapper;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteGeometry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteDataMapperTest {

    private final RouteDataMapper mapper = new RouteDataMapper(new RouteGeometryPointsCache());
    private BusRoute route;

    @BeforeEach
    void setUp() {
        RouteGeometry fwd = new RouteGeometry(List.of(
                Coordinates.of(37.9601, 58.3261),
                Coordinates.of(37.9701, 58.3361)));
        RouteGeometry bwd = new RouteGeometry(List.of(
                Coordinates.of(37.9701, 58.3361),
                Coordinates.of(37.9601, 58.3261)));
        route = BusRoute.create("29A", "Main", "tm", "en", "#FF5722", "ashgabat", 30)
                .updateRouteGeometry(fwd, bwd);
    }

    @Test
    void toRouteDataSyncPopulatesBasicFields() {
        RouteData data = mapper.toRouteDataSync(route);

        assertEquals(route.getId().getValue(), data.id());
        assertEquals("29A", data.routeNumber());
        assertEquals("Main", data.routeName());
        assertEquals("tm", data.nameTm());
        assertEquals("en", data.nameEn());
        assertEquals("#FF5722", data.routeColor());
        assertEquals(0L, data.activeVehiclesCount());
        assertTrue(data.forwardGeometry().size() >= 2);
        assertTrue(data.backwardGeometry().size() >= 2);
    }

    @Test
    void toRouteDataWrapsInMono() {
        StepVerifier.create(mapper.toRouteData(route))
                .assertNext(data -> assertEquals("29A", data.routeNumber()))
                .verifyComplete();
    }

    @Test
    void toRouteDataWithStopsEmitsCounts() {
        StepVerifier.create(mapper.toRouteDataWithStops(route, List.of(), List.of(), 3L))
                .assertNext(data -> {
                    assertEquals(3L, data.activeVehiclesCount());
                    assertEquals(0, data.forwardStopsCount());
                    assertEquals(0, data.backwardStopsCount());
                })
                .verifyComplete();
    }

    @Test
    void nullActiveVehicleCountDefaultsToZero() {
        RouteData data = mapper.toRouteDataWithStopsSync(route, List.of(), List.of(), null);
        assertEquals(0L, data.activeVehiclesCount());
    }

    @Test
    void distanceFieldsDerivedFromRouteGeometry() {
        RouteData data = mapper.toRouteDataSync(route);
        assertNotNull(data.totalDistanceForwardKm());
        assertTrue(data.totalDistanceForwardKm().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void distanceFieldsConvertMetersToKilometersRoundingHalfUp() {
        BusRoute routed = route.toBuilder()
                .totalDistanceForwardMeters(1234)
                .totalDistanceBackwardMeters(1555)
                .build();

        RouteData data = mapper.toRouteDataSync(routed);

        assertEquals(new BigDecimal("1.23"), data.totalDistanceForwardKm());
        assertEquals(new BigDecimal("1.56"), data.totalDistanceBackwardKm());
    }

    @Test
    void emptyGeometryReturnsEmptyLists() {
        BusRoute empty = BusRoute.create("1", "n", "", "", "#000000", "city", 10);

        RouteData data = mapper.toRouteDataSync(empty);

        assertNotNull(data.forwardGeometry());
        assertNotNull(data.backwardGeometry());
        assertTrue(data.forwardGeometry().isEmpty());
        assertTrue(data.backwardGeometry().isEmpty());
    }
}
