package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.routing.infrastructure.config.ETAProperties;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.DirectVehiclePositionBroadcaster;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PredictionBroadcasterColdStartTest {

    @Mock
    private DirectVehiclePositionBroadcaster directBroadcaster;

    @Mock
    private RouteGeometryCache routeGeometryCache;

    @Mock
    private ETAProperties etaProperties;

    @Test
    void coldStartInFuture_isInColdStart_true() {
        VehiclePredictionState state = VehiclePredictionState.builder()
                .vehicleId("v1")
                .coldStartUntilAt(Instant.now().plusSeconds(10))
                .build();

        assertThat(PredictionBroadcaster.isInColdStart(state)).isTrue();
    }

    @Test
    void coldStartInPast_isInColdStart_false() {
        VehiclePredictionState state = VehiclePredictionState.builder()
                .vehicleId("v1")
                .coldStartUntilAt(Instant.now().minusSeconds(1))
                .build();

        assertThat(PredictionBroadcaster.isInColdStart(state)).isFalse();
    }

    @Test
    void coldStartNull_isInColdStart_false() {
        VehiclePredictionState state = VehiclePredictionState.builder()
                .vehicleId("v1")
                .coldStartUntilAt(null)
                .build();

        assertThat(PredictionBroadcaster.isInColdStart(state)).isFalse();
    }

    @Test
    void broadcast_skipsPublish_whenInColdStart() {
        PredictionBroadcaster broadcaster = new PredictionBroadcaster(
                directBroadcaster, routeGeometryCache, etaProperties, new PredictionProperties(),
                new biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer());

        VehiclePredictionState state = VehiclePredictionState.builder()
                .vehicleId("v1")
                .licensePlate("TEST-001")
                .routeNumber("1")
                .inMotion(true)
                .speedKmh(30)
                .coldStartUntilAt(Instant.now().plusSeconds(10))
                .build();

        StepVerifier.create(broadcaster.broadcast(state)).verifyComplete();
        verify(directBroadcaster, never()).broadcastDirect(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void broadcast_emitsRawGpsFallback_whenInColdStartWithGps() {
        PredictionBroadcaster broadcaster = new PredictionBroadcaster(
                directBroadcaster, routeGeometryCache, etaProperties, new PredictionProperties(),
                new biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer());

        VehiclePredictionState state = VehiclePredictionState.builder()
                .vehicleId("v1")
                .licensePlate("TEST-001")
                .routeNumber("1")
                .inMotion(true)
                .speedKmh(30)
                .rawGpsSpeedKmh(22)
                .gpsLatitude(37.98)
                .gpsLongitude(58.40)
                .predictedLatitude(37.9)
                .predictedLongitude(58.3)
                .course(45.0)
                .direction(0)
                .routeCoordinates(List.of(new double[]{37.9, 58.3}, new double[]{37.91, 58.31}))
                .totalRouteDistanceMeters(1000)
                .fractionOnRoute(0.25)
                .coldStartUntilAt(Instant.now().plusSeconds(10))
                .lastReceivedAt(Instant.now())
                .build();

        StepVerifier.create(broadcaster.broadcast(state)).verifyComplete();

        ArgumentCaptor<VehiclePositionWebSocketMessage> captor =
                ArgumentCaptor.forClass(VehiclePositionWebSocketMessage.class);
        verify(directBroadcaster).broadcastDirect(captor.capture());
        VehiclePositionWebSocketMessage msg = captor.getValue();
        assertEquals(37.98, msg.getLatitude());
        assertEquals(58.40, msg.getLongitude());
        assertFalse(msg.getPredicted());
        assertEquals("RAW_GPS", msg.getConfidence());
    }
}
