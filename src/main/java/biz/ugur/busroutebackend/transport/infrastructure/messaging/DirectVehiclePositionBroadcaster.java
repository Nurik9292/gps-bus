package biz.ugur.busroutebackend.transport.infrastructure.messaging;

public interface DirectVehiclePositionBroadcaster {
    void broadcastDirect(VehiclePositionWebSocketMessage message);
}
