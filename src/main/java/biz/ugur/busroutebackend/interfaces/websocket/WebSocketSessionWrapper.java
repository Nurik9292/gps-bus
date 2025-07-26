package biz.ugur.busroutebackend.interfaces.websocket;

import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionDTO;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import reactor.core.Disposable;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Wrapper для WebSocket сессии с дополнительными возможностями
 */
@Slf4j
@Getter
@Setter
public class WebSocketSessionWrapper {

    private final String sessionId;
    private final WebSocketSession session;
    private final Instant connectedAt;
    private final AtomicLong messageCount = new AtomicLong(0);

    private String subscriptionType = "all"; // "all", "bounds", "routes"
    private Set<String> routeFilter;

    // Для geographical bounds подписки
    private Double minLat;
    private Double minLon;
    private Double maxLat;
    private Double maxLon;

    private Disposable updateSubscription;

    public WebSocketSessionWrapper(String sessionId, WebSocketSession session) {
        this.sessionId = sessionId;
        this.session = session;
        this.connectedAt = Instant.now();
    }

    public boolean isActive() {
        return session.isOpen();
    }

    public void sendMessage(TextMessage message) throws IOException {
        if (session.isOpen()) {
            synchronized (session) {
                session.sendMessage(message);
            }
        }
    }

    public void incrementMessageCount() {
        messageCount.incrementAndGet();
    }

    public void setBounds(double minLat, double minLon, double maxLat, double maxLon) {
        this.minLat = minLat;
        this.minLon = minLon;
        this.maxLat = maxLat;
        this.maxLon = maxLon;
    }

    /**
     * Проверяет, заинтересована ли сессия в обновлениях этого автобуса
     */
    public boolean isInterestedInVehicle(VehiclePositionWebSocketMessage message) {
        switch (subscriptionType) {
            case "all":
                return true;

            case "routes":
                return routeFilter != null && routeFilter.contains(getRouteFromMessage(message));

            case "bounds":
                return isWithinBounds(message.getLatitude(), message.getLongitude());

            default:
                return false;
        }
    }

    /**
     * Для VehiclePositionDTO (начальная загрузка)
     */
    public boolean isInterestedInVehicle(VehiclePositionDTO vehicle) {
        switch (subscriptionType) {
            case "all":
                return true;

            case "routes":
                return routeFilter != null && routeFilter.contains(vehicle.getRouteNumber());

            case "bounds":
                return isWithinBounds(vehicle.getCurrentLatitude(), vehicle.getCurrentLongitude());

            default:
                return false;
        }
    }

    private boolean isWithinBounds(Double lat, Double lon) {
        if (lat == null || lon == null || minLat == null || minLon == null || maxLat == null || maxLon == null) {
            return false;
        }

        return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
    }

    private String getRouteFromMessage(VehiclePositionWebSocketMessage message) {
        // В реальной реализации нужно получить номер маршрута из базы данных по vehicleId
        // Для простоты возвращаем пустую строку - это можно улучшить
        return "";
    }

    public void dispose() {
        if (updateSubscription != null && !updateSubscription.isDisposed()) {
            updateSubscription.dispose();
        }

        if (session.isOpen()) {
            try {
                session.close();
            } catch (IOException e) {
                log.warn("Failed to close WebSocket session {}: {}", sessionId, e.getMessage());
            }
        }
    }
}