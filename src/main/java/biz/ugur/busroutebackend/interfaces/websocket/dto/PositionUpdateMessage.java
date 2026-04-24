package biz.ugur.busroutebackend.interfaces.websocket.dto;

import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage;

import java.time.Instant;
import java.util.List;

public record PositionUpdateMessage(
        String type,
        int count,
        List<VehiclePositionWebSocketMessage> vehicles,
        String timestamp
) {
    public static PositionUpdateMessage of(List<VehiclePositionWebSocketMessage> vehicles) {
        return new PositionUpdateMessage("position_update", vehicles.size(), vehicles, Instant.now().toString());
    }
}
