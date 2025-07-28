package biz.ugur.busroutebackend.interfaces.websocket;

import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionDTO;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;


@Slf4j
@Getter
@Setter
public class WebSocketSessionWrapper {

    private final String sessionId;
    private final WebSocketSession session;
    private final Instant connectedAt;
    private final AtomicLong messageCount = new AtomicLong(0);

    private String subscriptionType = "all";
    private Set<String> routeFilter;

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


    public Mono<Void> sendMessage(String payload) {
        if (!isActive()) {
            return Mono.empty();
        }
        WebSocketMessage message = session.textMessage(payload);
        incrementMessageCount();
        return session.send(Mono.just(message)).then();
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


    public boolean isInterestedInVehicle(VehiclePositionWebSocketMessage message) {
        return switch (subscriptionType) {
            case "all" -> true;
            case "routes" -> routeFilter != null && routeFilter.contains(getRouteFromMessage(message));
            case "bounds" -> isWithinBounds(message.getLatitude(), message.getLongitude());
            default -> false;
        };
    }


    public boolean isInterestedInVehicle(VehiclePositionDTO vehicle) {
        return switch (subscriptionType) {
            case "all" -> true;
            case "routes" -> routeFilter != null && routeFilter.contains(vehicle.getRouteNumber());
            case "bounds" -> isWithinBounds(vehicle.getCurrentLatitude(), vehicle.getCurrentLongitude());
            default -> false;
        };
    }

    private boolean isWithinBounds(Double lat, Double lon) {
        if (lat == null || lon == null || minLat == null || minLon == null || maxLat == null || maxLon == null) {
            return false;
        }
        return lat >= minLat && lat <= maxLat && lon >= minLon && lon <= maxLon;
    }

    private String getRouteFromMessage(VehiclePositionWebSocketMessage message) {
        // В реальной реализации нужно получить номер маршрута из базы данных по vehicleId
        return "";
    }


    public Mono<Void> dispose() {
        if (updateSubscription != null && !updateSubscription.isDisposed()) {
            updateSubscription.dispose();
        }
        return session.close().onErrorResume(e -> {
            log.warn("Failed to close WebSocket session {}: {}", sessionId, e.getMessage());
            return Mono.empty();
        });
    }
}
