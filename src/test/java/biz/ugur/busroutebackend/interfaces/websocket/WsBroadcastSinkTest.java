package biz.ugur.busroutebackend.interfaces.websocket;

import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WsBroadcastSinkTest {

    @Mock
    private WebSocketBufferMetricsTracker metrics;

    @Mock
    private WsSessionRegistry sessionRegistry;

    private WsBroadcastSink sink;

    @BeforeEach
    void setUp() {
        sink = new WsBroadcastSink(metrics, sessionRegistry);
    }

    private VehiclePositionWebSocketMessage msg(String vehicleId, String plate, boolean predicted) {
        return new VehiclePositionWebSocketMessage(
                vehicleId, plate, "160", 37.9, 58.3, 25.0, true,
                LocalDateTime.now(), 90.0, false,
                java.util.List.of(), predicted, 0.5);
    }

    @Test
    void emitNullMessageIsNoOp() {
        sink.emit(null);
        verify(metrics, never()).recordEmitted();
        verify(metrics, never()).recordDropped(anyOrNull());
    }

    @Test
    void emitWithSubscriberRecordsEmitted() {
        Flux<VehiclePositionWebSocketMessage> flux = sink.asFlux();

        StepVerifier.create(flux)
                .then(() -> sink.emit(msg("v1", "1234 AGJ", true)))
                .assertNext(m -> assertThat(m.getVehicleId()).isEqualTo("v1"))
                .thenCancel()
                .verify();

        verify(metrics, times(1)).recordEmitted();
        verify(metrics, never()).recordDropped(anyOrNull());
    }

    @Test
    void emitWithoutSubscriberDoesNotRecordDropped() {
        sink.emit(msg("v1", "1234 AGJ", false));
        verify(metrics, never()).recordEmitted();
        verify(metrics, never()).recordDropped(anyOrNull());
    }

    @Test
    void broadcastDirectDelegatesToEmitAndLogsSinkLine() {
        Flux<VehiclePositionWebSocketMessage> flux = sink.asFlux();

        StepVerifier.create(flux)
                .then(() -> sink.broadcastDirect(msg("v2", "5678 AGI", false)))
                .assertNext(m -> {
                    assertThat(m.getVehicleId()).isEqualTo("v2");
                    assertThat(m.getLicensePlate()).isEqualTo("5678 AGI");
                })
                .thenCancel()
                .verify();

        verify(metrics, times(1)).recordEmitted();
    }

    @Test
    void broadcastDirectWithNullMessageIsNoOp() {
        sink.broadcastDirect(null);
        verify(metrics, never()).recordEmitted();
    }

    @Test
    void getStatsDelegatesToMetrics() {
        var stats = new WebSocketBufferMetricsTracker.BufferHealthStats(100L, 1L);
        org.mockito.Mockito.when(metrics.getStats()).thenReturn(stats);

        assertThat(sink.getStats()).isSameAs(stats);
    }

    @Test
    void isHealthyDelegatesToMetrics() {
        org.mockito.Mockito.when(metrics.isHealthy()).thenReturn(true);
        assertThat(sink.isHealthy()).isTrue();

        org.mockito.Mockito.when(metrics.isHealthy()).thenReturn(false);
        assertThat(sink.isHealthy()).isFalse();
    }

    private static String anyOrNull() {
        return org.mockito.ArgumentMatchers.nullable(String.class);
    }
}
