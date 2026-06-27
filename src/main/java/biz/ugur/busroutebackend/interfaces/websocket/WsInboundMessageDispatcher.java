package biz.ugur.busroutebackend.interfaces.websocket;

import biz.ugur.busroutebackend.interfaces.websocket.dto.ClientMessageType;
import biz.ugur.busroutebackend.interfaces.websocket.dto.WebSocketClientMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;

import java.util.Set;

@Component
@Slf4j
public class WsInboundMessageDispatcher {

    private final ObjectMapper objectMapper;
    private final WsSessionRegistry sessionRegistry;
    private final WsOutboundStreamBuilder outboundBuilder;

    public WsInboundMessageDispatcher(ObjectMapper objectMapper,
                                      WsSessionRegistry sessionRegistry,
                                      WsOutboundStreamBuilder outboundBuilder) {
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
        this.outboundBuilder = outboundBuilder;
    }

    public Mono<Void> dispatch(WebSocketSession session, String sessionId,
                                SessionConfig config, WebSocketMessage message) {
        config.touch();

        try {
            String payload = message.getPayloadAsText();
            if (payload == null || payload.isBlank()) {
                log.debug("Ignoring empty inbound frame from session {}", sessionId);
                return Mono.empty();
            }
            WebSocketClientMessage clientMessage = objectMapper.readValue(payload, WebSocketClientMessage.class);

            String messageType = clientMessage.getType();
            if (messageType == null) {
                log.debug("Received message without type from session {}", sessionId);
                return Mono.empty();
            }

            var typed = ClientMessageType.fromWireName(messageType);
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
                                              WebSocketClientMessage message) {
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
                                              WebSocketClientMessage message) {
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
}
