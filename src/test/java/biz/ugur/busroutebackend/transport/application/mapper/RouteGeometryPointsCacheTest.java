package biz.ugur.busroutebackend.transport.application.mapper;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteGeometryPointsCacheTest {

    private static final String WKT = "LINESTRING(58.4100 37.8900, 58.3900 37.8950)";

    private BusRoute routeWith(String forwardWkt, Long version) {
        return BusRoute.builder()
                .id(BusRouteId.of("route-x"))
                .routeNumber("99")
                .routeGeometryForward(forwardWkt)
                .version(version)
                .build();
    }

    @Test
    void forwardPoints_parsesOnceThenReturnsCachedInstanceForSameVersion() {
        RouteGeometryPointsCache cache = new RouteGeometryPointsCache();
        BusRoute route = routeWith(WKT, 1L);

        List<Coordinates> first = cache.forwardPoints(route);
        List<Coordinates> second = cache.forwardPoints(route);

        assertThat(first).hasSize(2);
        assertThat(second)
                .as("same route version must return the cached list without re-parsing")
                .isSameAs(first);
    }

    @Test
    void forwardPoints_reparsesWhenVersionChanges() {
        RouteGeometryPointsCache cache = new RouteGeometryPointsCache();

        List<Coordinates> v1 = cache.forwardPoints(routeWith(WKT, 1L));
        List<Coordinates> v2 = cache.forwardPoints(routeWith(WKT, 2L));

        assertThat(v2)
                .as("version bump (route edited) must invalidate and re-parse")
                .isNotSameAs(v1);
        assertThat(v2).hasSize(2);
    }

    @Test
    void forwardPoints_emptyForNullGeometry() {
        RouteGeometryPointsCache cache = new RouteGeometryPointsCache();
        assertThat(cache.forwardPoints(routeWith(null, 1L))).isEmpty();
    }
}
