package biz.ugur.busroutebackend.interfaces.rest.transport.V2.response;

import biz.ugur.busroutebackend.transport.application.dto.RouteStopDTO;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteDetailV2Test {

    private static RouteData routeWithOneForwardStop() {
        RouteStopDTO stop = new RouteStopDTO(
                "stop-1", "Awtokombinat", "CODE", 1, 0, 2, 100,
                new BigDecimal("37.95"), new BigDecimal("58.38"), true);
        return new RouteData(
                "route-legacy-60", "3", "Awtokombinat - Täze zaman", "tm", "en",
                "#7B1FA2", "city-001", true, 140, 1, 0,
                new BigDecimal("43.61"), new BigDecimal("34.81"),
                List.of(), List.of(), 3L, null, null, null,
                List.of(stop), List.of());
    }

    @Test
    void fromRouteData_mapsLightStops() {
        RouteDetailV2 v2 = RouteDetailV2.fromRouteData(routeWithOneForwardStop());

        assertThat(v2.forwardStops()).hasSize(1);
        RouteStopV2 s = v2.forwardStops().get(0);
        assertThat(s.id()).isEqualTo("stop-1");
        assertThat(s.name()).isEqualTo("Awtokombinat");
        assertThat(s.sequence()).isEqualTo(1);
        assertThat(s.location().lat()).isEqualByComparingTo("37.95");
        assertThat(s.location().lon()).isEqualByComparingTo("58.38");
        assertThat(s.distanceFromStartMeters()).isEqualTo(100);
    }

    @Test
    void serializesCamelCase_withoutGeometry() throws Exception {
        String json = new ObjectMapper().writeValueAsString(RouteDetailV2.fromRouteData(routeWithOneForwardStop()));

        assertThat(json)
                .as("camelCase + light stops present")
                .contains("\"routeNumber\"")
                .contains("\"forwardStops\"")
                .contains("\"name\":\"Awtokombinat\"")
                .contains("\"location\"");
        assertThat(json)
                .as("no geometry, no v1 snake_case keys")
                .doesNotContain("geometry")
                .doesNotContain("route_number")
                .doesNotContain("forward_stops_ids");
    }
}
