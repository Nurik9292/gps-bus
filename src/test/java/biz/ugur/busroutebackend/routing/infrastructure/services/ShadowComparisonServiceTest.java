package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorTimetable;
import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService.DirectRouteResult;
import biz.ugur.busroutebackend.routing.infrastructure.raptor.RaptorEngine;
import biz.ugur.busroutebackend.routing.infrastructure.raptor.RaptorTimetableCache;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShadowComparisonServiceTest {

    @Mock
    private DijkstraRouteCalculationService dijkstra;

    @Mock
    private RaptorTimetableCache raptorCache;

    @Mock
    private RaptorEngine raptorEngine;

    @Mock
    private RaptorJourneyMapper raptorMapper;

    @Mock
    private RoutingMetrics metrics;

    @InjectMocks
    private ShadowComparisonService service;

    private DirectRouteResult dijkstraResult;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "sampleRate", 1.0);
        ReflectionTestUtils.setField(service, "etaDiffThresholdSeconds", 300);
        ReflectionTestUtils.setField(service, "maxRounds", 4);
        ReflectionTestUtils.setField(service, "serviceDayStart", "06:00");

        dijkstraResult = new DirectRouteResult(
                stubRoute("1"),
                stubStop("A"),
                stubStop("B"),
                15, 0.0, 0.0, 0);
    }

    @Test
    void raptorErrorIsSwallowed_userStillGetsDijkstraResult() {
        List<BusStop> from = List.of(stubStop("A"));
        List<BusStop> to = List.of(stubStop("B"));

        when(dijkstra.findDirectRoutes(anyList(), anyList()))
                .thenReturn(Flux.just(dijkstraResult));
        when(raptorCache.getTimetable())
                .thenReturn(Mono.error(new RuntimeException("simulated raptor failure")));

        StepVerifier.create(service.findDirectRoutes(from, to))
                .expectNext(dijkstraResult)
                .verifyComplete();
    }

    @Test
    void delegatesAreStopsConnected_directlyToDijkstra() {
        BusStop a = stubStop("A");
        BusStop b = stubStop("B");
        when(dijkstra.areStopsConnected(a, b)).thenReturn(Mono.just(true));

        StepVerifier.create(service.areStopsConnected(a, b))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void dijkstraEmpty_userStillSeesEmpty() {
        List<BusStop> from = List.of(stubStop("A"));
        List<BusStop> to = List.of(stubStop("B"));

        when(dijkstra.findDirectRoutes(anyList(), anyList()))
                .thenReturn(Flux.empty());
        when(raptorCache.getTimetable())
                .thenReturn(Mono.just(RaptorTimetable.from(List.of(), List.of())));
        when(raptorEngine.findJourneys(any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of());

        StepVerifier.create(service.findDirectRoutes(from, to))
                .verifyComplete();
    }

    private BusStop stubStop(String id) {
        return BusStop.builder()
                .id(BusStopId.of("stop-" + id))
                .stopName("Stop " + id)
                .latitude(BigDecimal.valueOf(38.0))
                .longitude(BigDecimal.valueOf(58.0))
                .isActive(true)
                .isMajorStop(false)
                .cityId("city-001")
                .version(0L)
                .build();
    }

    private BusRoute stubRoute(String routeNumber) {
        return BusRoute.builder()
                .id(BusRouteId.of("route-" + routeNumber))
                .routeNumber(routeNumber)
                .routeName("Route " + routeNumber)
                .isActive(true)
                .estimatedDurationMinutes(60)
                .version(0L)
                .build();
    }
}
