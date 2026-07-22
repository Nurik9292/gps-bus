package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.routing.infrastructure.config.ETAProperties;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo;
import biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.DirectVehiclePositionBroadcaster;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PredictionBroadcasterTerminalDepartureTest {

    private static final Instant NOW = Instant.parse("2026-07-22T06:00:00Z");
    private static final java.time.Clock CLOCK =
            java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC);

    @Mock
    private DirectVehiclePositionBroadcaster directBroadcaster;
    @Mock
    private RouteGeometryCache routeGeometryCache;
    @Mock
    private TerminalDepartureEtaService terminalDepartureEtaService;

    private PredictionBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new PredictionBroadcaster(
                directBroadcaster, routeGeometryCache, new ETAProperties(),
                new PredictionProperties(), mock(VehiclePositionPredictor.class),
                new PipelineTracer(), new LiveFactorSnapshotHolder(), CLOCK,
                terminalDepartureEtaService);
        when(routeGeometryCache.getStopsAhead(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of());
        when(terminalDepartureEtaService.departureEtasForVehicle(anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
    }

    private static VehiclePredictionState.VehiclePredictionStateBuilder terminalBaseline() {
        return VehiclePredictionState.builder()
                .vehicleId("veh-1")
                .licensePlate("5670 AGJ")
                .routeNumber("23")
                .routeId("route-legacy-31")
                .direction(0)
                .directionConfirmed(true)
                .inMotion(false)
                .speedKmh(0)
                .rawGpsSpeedKmh(0)
                .predictedLatitude(37.944072)
                .predictedLongitude(58.404446)
                .routeCoordinates(List.of(
                        new double[]{37.944072, 58.404446},
                        new double[]{37.944100, 58.404500}))
                .totalRouteDistanceMeters(10_000)
                .fractionOnRoute(0.999)
                .course(38.8)
                .lastReceivedAt(NOW.minusSeconds(20));
    }

    private VehiclePositionWebSocketMessage capture() {
        ArgumentCaptor<VehiclePositionWebSocketMessage> captor =
                ArgumentCaptor.forClass(VehiclePositionWebSocketMessage.class);
        verify(directBroadcaster).broadcastDirect(captor.capture());
        return captor.getValue();
    }

    @Test
    void terminalBusEmitsDepartureEtasForReverseDirection() {
        when(terminalDepartureEtaService.departureEtasForVehicle(eq("veh-1"),
                eq("23"), eq("route-legacy-31"), eq(NOW)))
                .thenReturn(List.of(
                        new TerminalDepartureEtaService.DepartureStopEta("T", "Конечная", 180, 0, 1),
                        new TerminalDepartureEtaService.DepartureStopEta("S1", "Первая", 270, 800, 1)));

        StepVerifier.create(broadcaster.broadcast(terminalBaseline().build())).verifyComplete();

        VehiclePositionWebSocketMessage msg = capture();
        assertThat(msg.getNextStops()).hasSize(2);
        assertThat(msg.getNextStops().get(0).getStopId()).isEqualTo("T");
        assertThat(msg.getNextStops().get(0).getEtaMinutes()).isEqualTo(3);
        assertThat(msg.getNextStops().get(1).getEtaMinutes()).isEqualTo(5);
        assertThat(msg.getNextStops().get(1).getDistanceMeters()).isEqualTo(800);
    }

    @Test
    void withoutDepartureDataNextStopsStayNull() {
        StepVerifier.create(broadcaster.broadcast(terminalBaseline().build())).verifyComplete();
        assertThat(capture().getNextStops()).isNull();
    }

    @Test
    void computeNextStopsUsesRouteIdKeyedGeometryCache() {
        when(routeGeometryCache.getStopsAhead(eq("route-legacy-31"), eq(0), anyDouble()))
                .thenReturn(List.of(new RouteStopInfo("S9", "По ходу", null, null, 0, null,
                        9990, BigDecimal.valueOf(37.95), BigDecimal.valueOf(58.38), false)));

        StepVerifier.create(broadcaster.broadcast(terminalBaseline()
                .fractionOnRoute(0.5).build())).verifyComplete();

        VehiclePositionWebSocketMessage msg = capture();
        assertThat(msg.getNextStops())
                .as("геометрия берётся по routeId (кэш ключуется id, не номером)")
                .hasSize(1);
        assertThat(msg.getNextStops().get(0).getStopId()).isEqualTo("S9");
    }
}
