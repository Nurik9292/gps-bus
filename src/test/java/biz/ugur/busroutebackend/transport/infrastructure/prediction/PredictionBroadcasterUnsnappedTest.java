package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.routing.infrastructure.config.ETAProperties;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.DirectVehiclePositionBroadcaster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PredictionBroadcasterUnsnappedTest {

    @Mock
    private DirectVehiclePositionBroadcaster directBroadcaster;

    @Mock
    private RouteGeometryCache routeGeometryCache;

    @Mock
    private ETAProperties etaProperties;

    @Test
    void broadcast_suppressesWhenRouteCoordsPresentButFractionNegative() {
        PredictionBroadcaster broadcaster = new PredictionBroadcaster(
                directBroadcaster, routeGeometryCache, etaProperties, new PredictionProperties(), org.mockito.Mockito.mock(VehiclePositionPredictor.class),
                new biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer(), new LiveFactorSnapshotHolder(), java.time.Clock.systemUTC());

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
                .fractionOnRoute(-1)
                .lastReceivedAt(Instant.now())
                .build();

        StepVerifier.create(broadcaster.broadcast(state)).verifyComplete();
        verify(directBroadcaster, never()).broadcastDirect(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void broadcast_allowsWhenRouteCoordsNull_evenIfFractionNegative() {
        PredictionBroadcaster broadcaster = new PredictionBroadcaster(
                directBroadcaster, routeGeometryCache, etaProperties, new PredictionProperties(), org.mockito.Mockito.mock(VehiclePositionPredictor.class),
                new biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer(), new LiveFactorSnapshotHolder(), java.time.Clock.systemUTC());

        VehiclePredictionState state = VehiclePredictionState.builder()
                .vehicleId("v1")
                .licensePlate("TEST-002")
                .routeNumber(null)
                .inMotion(false)
                .speedKmh(0)
                .predictedLatitude(37.9)
                .predictedLongitude(58.3)
                .routeCoordinates(null)
                .fractionOnRoute(-1)
                .lastReceivedAt(Instant.now())
                .build();

        StepVerifier.create(broadcaster.broadcast(state)).verifyComplete();
        verify(directBroadcaster, never()).broadcastDirect(org.mockito.ArgumentMatchers.any());
    }
}
