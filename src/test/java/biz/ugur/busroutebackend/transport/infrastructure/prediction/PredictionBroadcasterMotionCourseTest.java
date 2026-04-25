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
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PredictionBroadcasterMotionCourseTest {

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
                new PredictionProperties(), new PipelineTracer());
        lenient().when(routeGeometryCache.getStopsAhead(anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of());
    }

    private static VehiclePredictionState.VehiclePredictionStateBuilder snappedBaseline() {
        return VehiclePredictionState.builder()
                .vehicleId("veh-1")
                .licensePlate("6917 AGE")
                .routeNumber("160")
                .direction(0)
                .inMotion(true)
                .speedKmh(48.0)
                .rawGpsSpeedKmh(48.0)
                .predictedLatitude(37.90250)
                .predictedLongitude(58.34425)
                .routeCoordinates(List.of(
                        new double[]{37.90250, 58.34425},
                        new double[]{37.90202, 58.34510}))
                .totalRouteDistanceMeters(10_000)
                .fractionOnRoute(0.5)
                .course(306.0)
                .lastReceivedAt(Instant.now());
    }

    @Test
    void firstBroadcast_usesRouteCourseFallback_whenNoPriorPosition() {
        VehiclePredictionState state = snappedBaseline().build();

        StepVerifier.create(broadcaster.broadcast(state)).verifyComplete();

        VehiclePositionWebSocketMessage msg = capture();
        assertThat(msg.getCourse()).isCloseTo(306.0, within(0.5));
    }

    @Test
    void secondBroadcast_replacesRouteCourseWithMotionCourse() {
        VehiclePredictionState first = snappedBaseline().build();
        StepVerifier.create(broadcaster.broadcast(first)).verifyComplete();

        VehiclePredictionState second = snappedBaseline()
                .predictedLatitude(37.90202)
                .predictedLongitude(58.34510)
                .build();
        StepVerifier.create(broadcaster.broadcast(second)).verifyComplete();

        VehiclePositionWebSocketMessage msg = captureNth(1);
        assertThat(msg.getCourse())
                .as("motion bearing 37.90250,58.34425 → 37.90202,58.34510 ≈ 126°, not route 306°")
                .isCloseTo(126.0, within(5.0));
    }

    @Test
    void stationary_holdsLastMotionCourse() {
        VehiclePredictionState first = snappedBaseline().build();
        StepVerifier.create(broadcaster.broadcast(first)).verifyComplete();
        VehiclePredictionState second = snappedBaseline()
                .predictedLatitude(37.90202)
                .predictedLongitude(58.34510)
                .build();
        StepVerifier.create(broadcaster.broadcast(second)).verifyComplete();

        VehiclePredictionState third = snappedBaseline()
                .predictedLatitude(37.90202)
                .predictedLongitude(58.34510)
                .speedKmh(0.5)
                .rawGpsSpeedKmh(0.5)
                .build();
        StepVerifier.create(broadcaster.broadcast(third)).verifyComplete();

        VehiclePositionWebSocketMessage msg = captureNth(2);
        assertThat(msg.getCourse())
                .as("stationary tick keeps the last motion course (sticky), not the stale route 306°")
                .isCloseTo(126.0, within(5.0));
    }

    @Test
    void microMovement_belowFiveMeters_holdsLastMotionCourse() {
        VehiclePredictionState first = snappedBaseline().build();
        StepVerifier.create(broadcaster.broadcast(first)).verifyComplete();

        VehiclePredictionState second = snappedBaseline()
                .predictedLatitude(37.90202)
                .predictedLongitude(58.34510)
                .build();
        StepVerifier.create(broadcaster.broadcast(second)).verifyComplete();

        VehiclePredictionState third = snappedBaseline()
                .predictedLatitude(37.90203)
                .predictedLongitude(58.34511)
                .build();
        StepVerifier.create(broadcaster.broadcast(third)).verifyComplete();

        VehiclePositionWebSocketMessage msg = captureNth(2);
        assertThat(msg.getCourse())
                .as("<5m jitter must not overwrite motion course")
                .isCloseTo(126.0, within(5.0));
    }

    @Test
    void directionFlip_resetsMotionCourseToRouteFallback() {
        VehiclePredictionState first = snappedBaseline().build();
        StepVerifier.create(broadcaster.broadcast(first)).verifyComplete();
        VehiclePredictionState second = snappedBaseline()
                .predictedLatitude(37.90202)
                .predictedLongitude(58.34510)
                .build();
        StepVerifier.create(broadcaster.broadcast(second)).verifyComplete();

        VehiclePredictionState flipped = snappedBaseline()
                .direction(1)
                .course(45.0)
                .predictedLatitude(37.90201)
                .predictedLongitude(58.34509)
                .build();
        StepVerifier.create(broadcaster.broadcast(flipped)).verifyComplete();

        VehiclePositionWebSocketMessage msg = captureNth(2);
        assertThat(msg.getCourse())
                .as("direction flip must drop sticky motion course and fall back to route course")
                .isCloseTo(45.0, within(0.5));
    }

    @Test
    void teleportLikeJump_overTwoHundredMeters_resetsToRouteFallback() {
        VehiclePredictionState first = snappedBaseline().build();
        StepVerifier.create(broadcaster.broadcast(first)).verifyComplete();
        VehiclePredictionState second = snappedBaseline()
                .predictedLatitude(37.90202)
                .predictedLongitude(58.34510)
                .build();
        StepVerifier.create(broadcaster.broadcast(second)).verifyComplete();

        VehiclePredictionState teleport = snappedBaseline()
                .course(220.0)
                .predictedLatitude(37.91500)
                .predictedLongitude(58.34510)
                .build();
        StepVerifier.create(broadcaster.broadcast(teleport)).verifyComplete();

        VehiclePositionWebSocketMessage msg = captureNth(2);
        assertThat(msg.getCourse())
                .as("jump > 200m drops sticky motion course and falls back to route course")
                .isCloseTo(220.0, within(0.5));
    }

    private VehiclePositionWebSocketMessage capture() {
        ArgumentCaptor<VehiclePositionWebSocketMessage> captor =
                ArgumentCaptor.forClass(VehiclePositionWebSocketMessage.class);
        verify(directBroadcaster).broadcastDirect(captor.capture());
        return captor.getValue();
    }

    private VehiclePositionWebSocketMessage captureNth(int index) {
        ArgumentCaptor<VehiclePositionWebSocketMessage> captor =
                ArgumentCaptor.forClass(VehiclePositionWebSocketMessage.class);
        verify(directBroadcaster, org.mockito.Mockito.atLeast(index + 1)).broadcastDirect(captor.capture());
        return captor.getAllValues().get(index);
    }
}
