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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PredictionBroadcasterOffRouteTest {

    @Mock
    private DirectVehiclePositionBroadcaster directBroadcaster;

    @Mock
    private RouteGeometryCache routeGeometryCache;

    @Mock
    private ETAProperties etaProperties;

    @Test
    void broadcast_suppressesWhenOffRouteAndRawGpsNotSet() {
        PredictionBroadcaster broadcaster = new PredictionBroadcaster(
                directBroadcaster, routeGeometryCache, etaProperties, new PredictionProperties(), org.mockito.Mockito.mock(VehiclePositionPredictor.class),
                new biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer());

        VehiclePredictionState state = VehiclePredictionState.builder()
                .vehicleId("v1")
                .licensePlate("TEST-001")
                .routeNumber("1")
                .inMotion(true)
                .speedKmh(30)
                .predictedLatitude(37.9)
                .predictedLongitude(58.3)
                .routeCoordinates(List.of(new double[]{37.9, 58.3}, new double[]{37.91, 58.31}))
                .totalRouteDistanceMeters(1000)
                .fractionOnRoute(0.25)
                .offRoute(true)
                .consecutiveOffRouteCount(5)
                .lastReceivedAt(Instant.now())
                .build();

        StepVerifier.create(broadcaster.broadcast(state)).verifyComplete();
        verify(directBroadcaster, never()).broadcastDirect(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void broadcast_emitsRawGpsFallback_whenOffRouteAndGpsAvailable() {
        PredictionBroadcaster broadcaster = new PredictionBroadcaster(
                directBroadcaster, routeGeometryCache, etaProperties, new PredictionProperties(), org.mockito.Mockito.mock(VehiclePositionPredictor.class),
                new biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer());

        VehiclePredictionState state = VehiclePredictionState.builder()
                .vehicleId("v1")
                .licensePlate("TEST-001")
                .routeNumber("1")
                .inMotion(true)
                .speedKmh(30)
                .rawGpsSpeedKmh(25)
                .gpsLatitude(37.95)
                .gpsLongitude(58.35)
                .predictedLatitude(37.9)
                .predictedLongitude(58.3)
                .course(90.0)
                .direction(0)
                .routeCoordinates(List.of(new double[]{37.9, 58.3}, new double[]{37.91, 58.31}))
                .totalRouteDistanceMeters(1000)
                .fractionOnRoute(0.25)
                .offRoute(true)
                .consecutiveOffRouteCount(5)
                .lastReceivedAt(Instant.now())
                .build();

        StepVerifier.create(broadcaster.broadcast(state)).verifyComplete();

        ArgumentCaptor<VehiclePositionWebSocketMessage> captor =
                ArgumentCaptor.forClass(VehiclePositionWebSocketMessage.class);
        verify(directBroadcaster).broadcastDirect(captor.capture());
        VehiclePositionWebSocketMessage msg = captor.getValue();
        assertEquals(37.95, msg.getLatitude());
        assertEquals(58.35, msg.getLongitude());
        assertFalse(msg.getPredicted());
        assertNull(msg.getFraction());
        assertEquals("RAW_GPS", msg.getConfidence());
    }
}
