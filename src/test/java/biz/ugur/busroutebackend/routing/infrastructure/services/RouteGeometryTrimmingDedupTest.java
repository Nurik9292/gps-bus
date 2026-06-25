package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.valueobject.StopCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteGeometryTrimmingDedupTest {

    private final RouteGeometryTrimmingService service =
            new RouteGeometryTrimmingService(new DistanceCalculationService());

    private BusStop stop(String name, double lat, double lon) {
        return BusStop.create(name, name, name,
                StopCode.of(name + "-CODE"),
                BigDecimal.valueOf(lat), BigDecimal.valueOf(lon),
                false, "city-001", "admin");
    }

    private List<String> points(String wkt) {
        String inner = wkt.substring(wkt.indexOf('(') + 1, wkt.lastIndexOf(')'));
        return Arrays.stream(inner.split(",")).map(String::trim).toList();
    }

    @Test
    void trimmedGeometryHasNoConsecutiveDuplicateVertices() {
        String wkt = "LINESTRING(58.300 37.900, 58.310 37.900, 58.320 37.900, 58.330 37.900)";
        BusStop from = stop("A", 37.900, 58.310);
        BusStop to = stop("B", 37.900, 58.330);

        String trimmed = service.trimRouteGeometry(wkt, from, to);

        List<String> points = points(trimmed);
        for (int i = 1; i < points.size(); i++) {
            assertThat(points.get(i))
                    .as("consecutive vertices must differ at index %d", i)
                    .isNotEqualTo(points.get(i - 1));
        }
    }
}
