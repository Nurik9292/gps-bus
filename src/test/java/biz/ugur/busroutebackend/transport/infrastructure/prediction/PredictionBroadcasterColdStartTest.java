package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.routing.infrastructure.config.ETAProperties;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.DirectVehiclePositionBroadcaster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
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
                directBroadcaster, routeGeometryCache, etaProperties, new PredictionProperties());

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
}
