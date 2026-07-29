package biz.ugur.busroutebackend.prediction.shadow;

import biz.ugur.busroutebackend.transport.infrastructure.prediction.RouteGeometryCache;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class V31RouteLinesS5Test {

    @Test
    void r5InvertedStopsDisableSingleRouteOnly() {
        RouteGeometryCache cache = mock(RouteGeometryCache.class);
        List<double[]> line = List.of(new double[]{38.0, 58.0}, new double[]{38.0, 58.05});
        when(cache.getPoints(any(), Mockito.anyInt())).thenReturn(line);
        when(cache.getCumulativeDistances(any(), Mockito.anyInt())).thenReturn(null);
        var stopFar = mock(biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo.class);
        when(stopFar.getStopId()).thenReturn("S-far");
        when(stopFar.getSequence()).thenReturn(1);
        when(stopFar.getLatitude()).thenReturn(java.math.BigDecimal.valueOf(38.0));
        when(stopFar.getLongitude()).thenReturn(java.math.BigDecimal.valueOf(58.04));
        var stopNear = mock(biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo.class);
        when(stopNear.getStopId()).thenReturn("S-near");
        when(stopNear.getSequence()).thenReturn(2);
        when(stopNear.getLatitude()).thenReturn(java.math.BigDecimal.valueOf(38.0));
        when(stopNear.getLongitude()).thenReturn(java.math.BigDecimal.valueOf(58.01));
        when(cache.getRouteStops(Mockito.eq("bad"), Mockito.anyInt()))
                .thenReturn(List.of(stopFar, stopNear));
        when(cache.getRouteStops(Mockito.eq("good"), Mockito.anyInt())).thenReturn(List.of());
        V31RouteLines lines = new V31RouteLines(cache);
        assertThat(lines.topologyFor("bad")).isNull();
        assertThat(lines.v31DisabledRoutes()).isEqualTo(1);
        assertThat(lines.topologyFor("good")).isNotNull();
        assertThat(lines.topologyFor("bad")).isNull();
        assertThat(lines.v31DisabledRoutes()).isEqualTo(1);
    }

    @Test
    void evictLetsFixedRouteComeBackWithoutRestart() {
        RouteGeometryCache cache = mock(RouteGeometryCache.class);
        List<double[]> line = List.of(new double[]{38.0, 58.0}, new double[]{38.0, 58.05});
        when(cache.getPoints(any(), Mockito.anyInt())).thenReturn(line);
        when(cache.getCumulativeDistances(any(), Mockito.anyInt())).thenReturn(null);
        var stopFar = mock(biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo.class);
        when(stopFar.getStopId()).thenReturn("S-far");
        when(stopFar.getSequence()).thenReturn(1);
        when(stopFar.getLatitude()).thenReturn(java.math.BigDecimal.valueOf(38.0));
        when(stopFar.getLongitude()).thenReturn(java.math.BigDecimal.valueOf(58.04));
        var stopNear = mock(biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo.class);
        when(stopNear.getStopId()).thenReturn("S-near");
        when(stopNear.getSequence()).thenReturn(2);
        when(stopNear.getLatitude()).thenReturn(java.math.BigDecimal.valueOf(38.0));
        when(stopNear.getLongitude()).thenReturn(java.math.BigDecimal.valueOf(58.01));
        when(cache.getRouteStops(Mockito.eq("fixable"), Mockito.anyInt()))
                .thenReturn(List.of(stopFar, stopNear));
        V31RouteLines lines = new V31RouteLines(cache);

        assertThat(lines.topologyFor("fixable")).isNull();
        assertThat(lines.topologyFor("fixable")).isNull();

        when(cache.getRouteStops(Mockito.eq("fixable"), Mockito.anyInt()))
                .thenReturn(List.of(stopNear, stopFar));
        assertThat(lines.topologyFor("fixable"))
                .as("без evict починенные данные не подхватываются")
                .isNull();

        lines.evict("fixable", "4");
        assertThat(lines.topologyFor("fixable"))
                .as("после evict топология пере-строится по починенным данным")
                .isNotNull();
    }
}
