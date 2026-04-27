package biz.ugur.busroutebackend.interfaces.websocket;

import biz.ugur.busroutebackend.transport.infrastructure.messaging.DirectVehiclePositionBroadcaster;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
@Slf4j
public class WsBroadcastSink implements DirectVehiclePositionBroadcaster {

    private final WebSocketBufferMetricsTracker bufferMetrics;
    private final WsSessionRegistry sessionRegistry;

    private final Sinks.Many<VehiclePositionWebSocketMessage> sink =
            Sinks.many().multicast().directBestEffort();

    public WsBroadcastSink(WebSocketBufferMetricsTracker bufferMetrics,
                           WsSessionRegistry sessionRegistry) {
        this.bufferMetrics = bufferMetrics;
        this.sessionRegistry = sessionRegistry;
    }

    public Flux<VehiclePositionWebSocketMessage> asFlux() {
        return sink.asFlux();
    }

    public void emit(VehiclePositionWebSocketMessage message) {
        if (message == null) {
            log.warn("Cannot broadcast null WebSocket message");
            return;
        }

        Sinks.EmitResult result = sink.tryEmitNext(message);
        if (result == Sinks.EmitResult.OK) {
            bufferMetrics.recordEmitted();
        } else if (result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER
                && result != Sinks.EmitResult.FAIL_CANCELLED) {
            bufferMetrics.recordDropped(message.getVehicleId());
            log.debug("Dropped position update for vehicle {}: {}", message.getVehicleId(), result);
        }
    }

    @Override
    public void broadcastDirect(VehiclePositionWebSocketMessage message) {
        if (message != null) {
            log.debug("[GPS_PIPELINE] WS_SINK vehicle={} plate={} type={} sessions={}",
                    message.getVehicleId(), message.getLicensePlate(),
                    Boolean.TRUE.equals(message.getPredicted()) ? "PRED" : "GPS",
                    sessionRegistry.activeCount());
        }
        emit(message);
    }

    public WebSocketBufferMetricsTracker.BufferHealthStats getStats() {
        return bufferMetrics.getStats();
    }

    public boolean isHealthy() {
        return bufferMetrics.isHealthy();
    }
}
