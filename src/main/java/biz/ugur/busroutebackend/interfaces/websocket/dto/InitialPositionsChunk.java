package biz.ugur.busroutebackend.interfaces.websocket.dto;

import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record InitialPositionsChunk(
        String type,
        @JsonProperty("page") long page,
        int count,
        @JsonProperty("last") boolean last,
        List<VehiclePositionWebSocketMessage> vehicles,
        String timestamp
) {
    public static InitialPositionsChunk of(long page, boolean last, List<VehiclePositionWebSocketMessage> vehicles) {
        return new InitialPositionsChunk(
                "initial_positions",
                page,
                vehicles.size(),
                last,
                vehicles,
                Instant.now().toString());
    }
}
