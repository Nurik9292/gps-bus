package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService.DirectRouteResult;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DirectRouteSearchServiceTest {

    @Test
    void dedupeByRouteNumberAndLimit_returnsAllUniqueRoutes_whenManyDistinctRouteNumbers() {
        List<DirectRouteResult> sorted = List.of(
                result("29", "stop-1", 10),
                result("129", "stop-1", 12),
                result("44", "stop-1", 14),
                result("70", "stop-1", 16),
                result("13", "stop-1", 18)
        );

        List<DirectRouteResult> deduped = DirectRouteSearchService.dedupeByRouteNumberAndLimit(sorted);

        assertThat(deduped).extracting(r -> r.route().getRouteNumber())
                .containsExactly("29", "129", "44", "70", "13");
    }

    @Test
    void dedupeByRouteNumberAndLimit_returnsFiveUniqueRouteNumbers_when22CandidatesIncludeDuplicates() {
        List<DirectRouteResult> sorted = List.of(
                result("29", "stop-A", 8),
                result("29", "stop-B", 10),
                result("29", "stop-C", 12),
                result("129", "stop-A", 9),
                result("129", "stop-B", 11),
                result("13", "stop-A", 13),
                result("15", "stop-A", 14),
                result("15", "stop-B", 16),
                result("44", "stop-A", 17),
                result("70", "stop-A", 19)
        );

        List<DirectRouteResult> deduped = DirectRouteSearchService.dedupeByRouteNumberAndLimit(sorted);

        assertThat(deduped).hasSize(5);
        assertThat(deduped).extracting(r -> r.route().getRouteNumber())
                .containsExactly("29", "129", "13", "15", "44");
    }

    @Test
    void dedupeByRouteNumberAndLimit_keepsFirstOccurrencePerRouteNumber() {
        List<DirectRouteResult> sorted = List.of(
                result("29", "stop-best", 8),
                result("29", "stop-worse", 12),
                result("29", "stop-worst", 18)
        );

        List<DirectRouteResult> deduped = DirectRouteSearchService.dedupeByRouteNumberAndLimit(sorted);

        assertThat(deduped).hasSize(1);
        assertThat(deduped.get(0).fromStop().getId().getValue()).isEqualTo("stop-best");
        assertThat(deduped.get(0).estimatedTravelMinutes()).isEqualTo(8);
    }

    @Test
    void dedupeByRouteNumberAndLimit_returnsLessThanMax_whenFewUniqueRouteNumbers() {
        List<DirectRouteResult> sorted = List.of(
                result("29", "stop-A", 10),
                result("29", "stop-B", 12),
                result("29", "stop-C", 14)
        );

        List<DirectRouteResult> deduped = DirectRouteSearchService.dedupeByRouteNumberAndLimit(sorted);

        assertThat(deduped).hasSize(1);
        assertThat(deduped.get(0).route().getRouteNumber()).isEqualTo("29");
    }

    @Test
    void dedupeByRouteNumberAndLimit_emptyInput_returnsEmpty() {
        List<DirectRouteResult> deduped = DirectRouteSearchService.dedupeByRouteNumberAndLimit(List.of());
        assertThat(deduped).isEmpty();
    }

    private DirectRouteResult result(String routeNumber, String fromStopId, int travelMinutes) {
        BusRoute route = BusRoute.restore(
                BusRouteId.of("route-" + routeNumber), routeNumber, "Route " + routeNumber,
                null, null, "#1976D2",
                true, null, 20,
                null, null, null, null,
                null, null, 0L
        );
        BusStop fromStop = BusStop.restore(
                BusStopId.of(fromStopId), fromStopId, null, null, null,
                BigDecimal.valueOf(37.9), BigDecimal.valueOf(58.4),
                true, false, null, null, null, 0L
        );
        BusStop toStop = BusStop.restore(
                BusStopId.of("dest-" + routeNumber), "dest-" + routeNumber, null, null, null,
                BigDecimal.valueOf(37.95), BigDecimal.valueOf(58.45),
                true, false, null, null, null, 0L
        );
        return new DirectRouteResult(route, fromStop, toStop, travelMinutes, 0.1, 0.1, 0);
    }
}
