package biz.ugur.busroutebackend.interfaces.websocket;


import java.time.Instant;
import java.util.Map;



public record WebSocketStatsDTO(
        int totalSessions,
        int activeSessions,
        Map<String, Long> subscriptionTypes,
        Instant timestamp) {
}