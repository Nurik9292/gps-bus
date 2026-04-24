package biz.ugur.busroutebackend.interfaces.websocket.dto;

import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage;

import java.time.Instant;
import java.util.List;

public record InitialPositionsMessage(
        String type,
        int count,
        List<VehiclePositionWebSocketMessage> vehicles,
        String timestamp
) {
    public static InitialPositionsMessage of(List<VehiclePositionWebSocketMessage> vehicles) {
        return new InitialPositionsMessage("initial_positions", vehicles.size(), vehicles, Instant.now().toString());
    }
}
