package biz.ugur.busroutebackend.interfaces.websocket;

import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePositionPredictionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class VehiclePositionHandler implements WebSocketHandler {

    private final ObjectMapper objectMapper;
    private final VehiclePositionPredictionService predictionService;
    private final WsSessionRegistry sessionRegistry;
    private final WsOutboundStreamBuilder outboundBuilder;

    public VehiclePositionHandler(ObjectMapper objectMapper,
                                  @org.springframework.context.annotation.Lazy VehiclePositionPredictionService predictionService,
                                  WsSessionRegistry sessionRegistry,
                                  WsOutboundStreamBuilder outboundBuilder) {
        this.objectMapper = objectMapper;
        this.predictionService = predictionService;
        this.sessionRegistry = sessionRegistry;
        this.outboundBuilder = outboundBuilder;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = sessionRegistry.register(session);
        SessionConfig sessionConfig = sessionRegistry.get(sessionId).orElseThrow();

        log.info("WebSocket connection established: {} from {} (total: {}, routes: {})",
                sessionId, sessionConfig.getClientIp(), sessionRegistry.activeCount(), sessionConfig.getRouteFilter());

        Flux<WebSocketMessage> outbound = outboundBuilder.buildFor(session, sessionConfig);

        Mono<Void> inbound = session.receive()
                .flatMap(message -> handleIncomingMessage(session, sessionId, sessionConfig, message))
                .doOnError(error -> {
                    String msg = error.getMessage();
                    if (msg != null && msg.contains("Connection has been closed")) {
                        log.debug("Client disconnected for session {}: {}", sessionId, msg);
                    } else {
                        log.warn("Error in inbound stream for session {}: {}", sessionId, msg);
                    }
                })
                .then();

        return session.send(outbound)
                .and(inbound)
                .doFinally(signalType -> {
                    sessionRegistry.remove(sessionId);
                    log.info("WebSocket connection closed: {} from {} (total: {}, signal: {})",
                            sessionId, sessionConfig.getClientIp(), sessionRegistry.activeCount(), signalType);
                });
    }

    private Mono<Void> handleIncomingMessage(WebSocketSession session, String sessionId,
                                              SessionConfig config, WebSocketMessage message) {
        config.touch();

        try {
            String payload = message.getPayloadAsText();
            var clientMessage = objectMapper.readValue(payload,
                    biz.ugur.busroutebackend.interfaces.websocket.dto.WebSocketClientMessage.class);

            String messageType = clientMessage.getType();
            if (messageType == null) {
                log.debug("Received message without type from session {}", sessionId);
                return Mono.empty();
            }

            var typed = biz.ugur.busroutebackend.interfaces.websocket.dto.ClientMessageType.fromWireName(messageType);
            if (typed.isEmpty()) {
                log.debug("Unknown message type from session {}: {}", sessionId, messageType);
                return Mono.empty();
            }
            return switch (typed.get()) {
                case PING -> handlePing(sessionId, config);
                case SUBSCRIBE_ROUTES -> handleSubscribeRoutes(session, sessionId, config, clientMessage);
                case SUBSCRIBE_BOUNDS -> handleSubscribeBounds(session, sessionId, config, clientMessage);
            };
        } catch (Exception e) {
            log.warn("Failed to handle message from session {}: {}", sessionId, e.getMessage());
            return Mono.empty();
        }
    }

    private Mono<Void> handlePing(String sessionId, SessionConfig config) {
        config.touch();
        log.debug("Received ping from session {}", sessionId);
        return Mono.empty();
    }

    private Mono<Void> handleSubscribeRoutes(WebSocketSession session, String sessionId, SessionConfig config,
                                              biz.ugur.busroutebackend.interfaces.websocket.dto.WebSocketClientMessage message) {
        if (!message.hasRoutes()) {
            log.debug("Subscribe routes message without routes from session {}", sessionId);
            return Mono.empty();
        }

        Set<String> oldFilter = config.getRouteFilter();
        config.setRouteFilter(Set.copyOf(message.getRoutes()));
        config.setSubscriptionType("routes");

        log.info("Session {} updated route subscription: {} -> {} (total active sessions: {})",
                sessionId, oldFilter, message.getRoutes(), sessionRegistry.activeCount());

        if (log.isDebugEnabled()) {
            sessionRegistry.snapshotForLog().forEach((sid, cfg) ->
                    log.debug("Active session {}: type={}, routes={}",
                            sid, cfg.getSubscriptionType(), cfg.getRouteFilter()));
        }

        return outboundBuilder.sendCurrentPositions(session, config)
                .doOnSuccess(v -> log.debug("Sent updated positions for route change"))
                .doOnError(error -> log.warn("Error sending positions after route change: {}", error.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    private Mono<Void> handleSubscribeBounds(WebSocketSession session, String sessionId, SessionConfig config,
                                              biz.ugur.busroutebackend.interfaces.websocket.dto.WebSocketClientMessage message) {
        if (!message.hasValidBounds()) {
            log.debug("Subscribe bounds message with invalid bounds from session {}", sessionId);
            return Mono.empty();
        }

        var bounds = message.getBounds();
        config.setBounds(bounds.get(0), bounds.get(1), bounds.get(2), bounds.get(3));
        config.setSubscriptionType("bounds");

        log.info("Session {} updated bounds subscription: {}", sessionId, config.getBoundsString());

        return outboundBuilder.sendCurrentPositions(session, config)
                .doOnSuccess(v -> log.debug("Sent updated positions for bounds change"))
                .doOnError(error -> log.warn("Error sending positions after bounds change: {}", error.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    public long getTotalExpiredSessions() {
        return sessionRegistry.totalExpired();
    }

    public Mono<WebSocketStatsDTO> getConnectionStats() {
        return Mono.fromCallable(() -> {
            int totalSessions = sessionRegistry.activeCount();
            Map<String, Long> subscriptionTypes = sessionRegistry.subscriptionTypeCounts();

            return new WebSocketStatsDTO(
                    totalSessions,
                    totalSessions,
                    subscriptionTypes,
                    LocalDateTime.now()
            );
        });
    }
}
