package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.routing.infrastructure.config.ETAProperties;
import biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.DirectVehiclePositionBroadcaster;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PredictionBroadcasterStationaryTest {

    @Mock
    private DirectVehiclePositionBroadcaster directBroadcaster;

    @Mock
    private RouteGeometryCache routeGeometryCache;

    private final ETAProperties etaProperties = new ETAProperties();

    private PredictionBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        broadcaster = new PredictionBroadcaster(
                directBroadcaster, routeGeometryCache, etaProperties,
                new PredictionProperties(), org.mockito.Mockito.mock(VehiclePositionPredictor.class),
                new PipelineTracer(), new LiveFactorSnapshotHolder(), java.time.Clock.systemUTC());
        lenient().when(routeGeometryCache.getStopsAhead(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of());
    }

    private static VehiclePredictionState.VehiclePredictionStateBuilder snappedBaseline() {
        return VehiclePredictionState.builder()
                .vehicleId("veh-1")
                .licensePlate("6599 AGI")
                .routeNumber("160")
                .direction(0)
                .predictedLatitude(37.944072)
                .predictedLongitude(58.404446)
                .routeCoordinates(List.of(
                        new double[]{37.944072, 58.404446},
                        new double[]{37.944100, 58.404500}))
                .totalRouteDistanceMeters(10_000)
                .fractionOnRoute(0.6840)
                .course(38.8)
                .lastReceivedAt(Instant.now().minusSeconds(20));
    }

    @Test
    void broadcastsZeroSpeedWhenRawGpsStationaryAndInMotionFalse() {
        VehiclePredictionState state = snappedBaseline()
                .speedKmh(33.4)
                .rawGpsSpeedKmh(0.0)
                .inMotion(false)
                .build();

        StepVerifier.create(broadcaster.broadcast(state)).verifyComplete();

        VehiclePositionWebSocketMessage msg = capture();
        assertThat(msg.getSpeedKmh())
                .as("Kalman residual 33.4 km/h must not leak when raw GPS says stationary")
                .isEqualTo(0.0);
        assertThat(msg.getIsInMotion()).isFalse();
    }

    @Test
    void broadcastsKalmanSpeedWhenActuallyMoving() {
        VehiclePredictionState state = snappedBaseline()
                .speedKmh(35.0)
                .rawGpsSpeedKmh(33.0)
                .inMotion(true)
                .build();

        StepVerifier.create(broadcaster.broadcast(state)).verifyComplete();

        VehiclePositionWebSocketMessage msg = capture();
        assertThat(msg.getSpeedKmh())
                .as("Kalman-smoothed 35.0 should be broadcast for genuinely moving bus")
                .isEqualTo(35.0);
        assertThat(msg.getIsInMotion()).isTrue();
    }

    @Test
    void keepsKalmanSpeedWhenRawSpeedLowButInMotionTrue() {
        VehiclePredictionState state = snappedBaseline()
                .speedKmh(2.0)
                .rawGpsSpeedKmh(0.5)
                .inMotion(true)
                .build();

        StepVerifier.create(broadcaster.broadcast(state)).verifyComplete();

        VehiclePositionWebSocketMessage msg = capture();
        assertThat(msg.getSpeedKmh())
                .as("inMotion=true overrides raw-speed-stationary check (slow crawl through traffic)")
                .isEqualTo(2.0);
    }

    @Test
    void overridesEvenWhenFreshGpsBecauseFreshBranchAlsoUsesRawSpeed() {
        VehiclePredictionState state = snappedBaseline()
                .lastReceivedAt(Instant.now())
                .speedKmh(33.4)
                .rawGpsSpeedKmh(0.0)
                .inMotion(false)
                .build();

        StepVerifier.create(broadcaster.broadcast(state)).verifyComplete();

        VehiclePositionWebSocketMessage msg = capture();
        assertThat(msg.getSpeedKmh()).isEqualTo(0.0);
        assertThat(msg.getIsInMotion()).isFalse();
    }

    private VehiclePositionWebSocketMessage capture() {
        ArgumentCaptor<VehiclePositionWebSocketMessage> captor =
                ArgumentCaptor.forClass(VehiclePositionWebSocketMessage.class);
        verify(directBroadcaster).broadcastDirect(captor.capture());
        return captor.getValue();
    }
}
