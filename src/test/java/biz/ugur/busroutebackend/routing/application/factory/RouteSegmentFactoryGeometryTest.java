package biz.ugur.busroutebackend.routing.application.factory;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.domain.services.WalkingRouteService;
import biz.ugur.busroutebackend.routing.domain.valueobjects.RouteSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteSegmentFactoryGeometryTest {

    private static final Coordinates FROM = Coordinates.of(37.90395, 58.39808);
    private static final Coordinates TO = Coordinates.of(37.90971, 58.40225);

    private final RouteSegmentFactory factory = new RouteSegmentFactory();

    @Test
    void walkSegmentGetsStraightLineGeometryWhenOsrmEmpty() {
        RouteSegment segment = factory.createWalkingSegment(FROM, TO, 5,
                WalkingRouteService.WalkingRouteResult.EMPTY);

        assertThat(segment.getWalkingGeometry()).containsExactly(
                List.of(FROM.getLatitudeAsDouble(), FROM.getLongitudeAsDouble()),
                List.of(TO.getLatitudeAsDouble(), TO.getLongitudeAsDouble()));
        assertThat(segment.getTotalDistanceMeters()).isPositive();
    }

    @Test
    void walkSegmentUsesOsrmGeometryWhenAvailable() {
        List<List<Double>> osrm = List.of(
                List.of(37.90395, 58.39808),
                List.of(37.90600, 58.40000),
                List.of(37.90971, 58.40225));
        WalkingRouteService.WalkingRouteResult result =
                new WalkingRouteService.WalkingRouteResult(osrm, 640);

        RouteSegment segment = factory.createWalkingSegment(FROM, TO, 5, result);

        assertThat(segment.getWalkingGeometry()).isEqualTo(osrm);
        assertThat(segment.getTotalDistanceMeters()).isEqualTo(640);
    }
}
