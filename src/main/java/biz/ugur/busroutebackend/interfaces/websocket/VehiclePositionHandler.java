package biz.ugur.busroutebackend.interfaces.websocket;

import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionDTO;
import biz.ugur.busroutebackend.transport.application.usecase.GetActiveVehiclesUseCase;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class VehiclePositionHandler implements WebSocketHandler {

    private final GetActiveVehiclesUseCase getActiveVehiclesUseCase;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private final Map<String, SessionConfig> activeSessions = new ConcurrentHashMap<>();
    private final AtomicInteger sessionCounter = new AtomicInteger(0);

    private final Sinks.Many<VehiclePositionWebSocketMessage> broadcastSink =
            Sinks.many().multicast().onBackpressureBuffer();

    public VehiclePositionHandler(GetActiveVehiclesUseCase getActiveVehiclesUseCase,
                                           ReactiveRedisTemplate<String, Object> redisTemplate,
                                           ObjectMapper objectMapper) {
        this.getActiveVehiclesUseCase = getActiveVehiclesUseCase;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;

        subscribeToRedisUpdates();
    }


    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = generateSessionId();
        SessionConfig sessionConfig = parseSessionConfig(session);
        activeSessions.put(sessionId, sessionConfig);

        log.info("WebSocket connection established: {} (total: {})",
                sessionId, activeSessions.size());

        Flux<WebSocketMessage> outbound = createOutboundMessageStream(session, sessionConfig);

        Mono<Void> inbound = session.receive()
                .doOnNext(message -> handleIncomingMessage(sessionId, sessionConfig, message))
                .doOnError(error -> log.error("Error in inbound stream for session {}: {}",
                        sessionId, error.getMessage()))
                .then();

        return session.send(outbound)
                .and(inbound)
                .doFinally(signalType -> {
                    activeSessions.remove(sessionId);
                    log.info("WebSocket connection closed: {} (total: {}, signal: {})",
                            sessionId, activeSessions.size(), signalType);
                });
    }

    private Flux<WebSocketMessage> createOutboundMessageStream(WebSocketSession session,
                                                               SessionConfig config) {
        Flux<WebSocketMessage> initialPositions = getInitialPositions(session, config);

        Flux<WebSocketMessage> liveUpdates = getLivePositionUpdates(session, config);

        Flux<WebSocketMessage> heartbeat = getHeartbeatMessages(session);

        return Flux.merge(initialPositions, liveUpdates, heartbeat)
                .onErrorContinue((error, obj) ->
                        log.warn("Error sending message: {}", error.getMessage()));
    }

    private Flux<WebSocketMessage> getInitialPositions(WebSocketSession session,
                                                       SessionConfig config) {
        return getActiveVehiclesUseCase.execute(null)
                .filter(vehicle -> isVehicleInScope(vehicle, config))
                .take(100)
                .map(this::convertToWebSocketMessage)
                .collectList()
                .map(positions -> {
                    try {
                        Map<String, Object> response = Map.of(
                                "type", "initial_positions",
                                "count", positions.size(),
                                "vehicles", positions,
                                "timestamp", Instant.now().toString()
                        );
                        return session.textMessage(objectMapper.writeValueAsString(response));
                    } catch (JsonProcessingException e) {
                        log.error("Error serializing initial positions: {}", e.getMessage());
                        return session.textMessage("{\"type\":\"error\",\"message\":\"Serialization error\"}");
                    }
                })
                .flux()
                .doOnNext(msg -> log.debug("Sent initial positions to session"));
    }

    private Flux<WebSocketMessage> getLivePositionUpdates(WebSocketSession session,
                                                          SessionConfig config) {
        return broadcastSink.asFlux()
                .filter(positionMsg -> isPositionInScope(positionMsg, config))
                .map(positionMsg -> {
                    try {
                        Map<String, Object> response = Map.of(
                                "type", "position_update",
                                "vehicle", positionMsg,
                                "timestamp", Instant.now().toString()
                        );
                        return session.textMessage(objectMapper.writeValueAsString(response));
                    } catch (JsonProcessingException e) {
                        log.warn("Error serializing position update: {}", e.getMessage());
                        return session.textMessage("{\"type\":\"error\",\"message\":\"Serialization error\"}");
                    }
                })
                .onErrorContinue((error, obj) ->
                        log.warn("Error in live updates: {}", error.getMessage()));
    }

    private Flux<WebSocketMessage> getHeartbeatMessages(WebSocketSession session) {
        return Flux.interval(Duration.ofSeconds(30))
                .map(tick -> {
                    try {
                        Map<String, Object> heartbeat = Map.of(
                                "type", "heartbeat",
                                "timestamp", Instant.now().toString(),
                                "session_active", true
                        );
                        return session.textMessage(objectMapper.writeValueAsString(heartbeat));
                    } catch (JsonProcessingException e) {
                        return session.textMessage("{\"type\":\"heartbeat\"}");
                    }
                });
    }

    private void handleIncomingMessage(String sessionId, SessionConfig config, WebSocketMessage message) {
        try {
            String payload = message.getPayloadAsText();
            @SuppressWarnings("unchecked")
            Map<String, Object> clientMessage = objectMapper.readValue(payload, Map.class);

            String messageType = (String) clientMessage.get("type");

            switch (messageType) {
                case "ping":
                    log.debug("Received ping from session {}", sessionId);
                    break;

                case "subscribe_routes":
                    @SuppressWarnings("unchecked")
                    java.util.List<String> routes = (List<String>) clientMessage.get("routes");
                    if (routes != null) {
                        config.setRouteFilter(Set.copyOf(routes));
                        config.setSubscriptionType("routes");
                        log.debug("Session {} updated route subscription: {}", sessionId, routes);
                    }
                    break;

                case "subscribe_bounds":
                    @SuppressWarnings("unchecked")
                    java.util.List<Double> bounds = (List<Double>) clientMessage.get("bounds");
                    if (bounds != null && bounds.size() == 4) {
                        config.setBounds(bounds.get(0), bounds.get(1), bounds.get(2), bounds.get(3));
                        config.setSubscriptionType("bounds");
                        log.debug("Session {} updated bounds subscription", sessionId);
                    }
                    break;

                default:
                    log.debug("Unknown message type from session {}: {}", sessionId, messageType);
            }
        } catch (Exception e) {
            log.warn("Failed to handle message from session {}: {}", sessionId, e.getMessage());
        }
    }

    private void subscribeToRedisUpdates() {
        log.info("Subscribing to Redis channel vehicle-position-updates...");
        redisTemplate.listenToChannel("vehicle-position-updates")
                .filter(Objects::nonNull)
                .mapNotNull(message -> {
                    Object messageObj = message.getMessage();
                    try {
                        return objectMapper.readValue(
                                message.getMessage().toString(),
                                VehiclePositionWebSocketMessage.class
                        );

                    } catch (Exception e) {
                        log.warn("Error parsing Redis message: {}", e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .subscribe(
                        positionMessage -> {
                            Sinks.EmitResult result = broadcastSink.tryEmitNext(positionMessage);
                            if (result.isFailure()) {
                                log.warn("Failed to emit position update: {}", result);
                            }
                        },
                        error -> log.error("Redis subscription error: {}", error.getMessage()),
                        () -> log.info("Redis subscription completed")
                );
    }

    public void broadcastVehiclePosition(VehiclePositionWebSocketMessage message) {
        Sinks.EmitResult result = broadcastSink.tryEmitNext(message);
        if (result.isFailure()) {
            log.warn("Failed to broadcast vehicle position: {}", result);
        }
    }

    public Mono<WebSocketStatsDTO> getConnectionStats() {
        return Mono.fromCallable(() -> {
            int totalSessions = activeSessions.size();

            Map<String, Long> subscriptionTypes = activeSessions.values().stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            SessionConfig::getSubscriptionType,
                            java.util.stream.Collectors.counting()
                    ));

            return new WebSocketStatsDTO(
                    totalSessions,
                    totalSessions, // В reactive WebSocket все сессии активны
                    subscriptionTypes,
                    Instant.now()
            );
        });
    }

    private String generateSessionId() {
        return "ws-" + sessionCounter.incrementAndGet() + "-" + System.currentTimeMillis();
    }

    private SessionConfig parseSessionConfig(WebSocketSession session) {
        SessionConfig config = new SessionConfig();

        String query = session.getHandshakeInfo().getUri().getQuery();
        if (query == null) {
            config.setSubscriptionType("all");
            return config;
        }

        Map<String, String> params = parseQueryString(query);

        if (params.containsKey("bounds")) {
            String[] bounds = params.get("bounds").split(",");
            if (bounds.length == 4) {
                try {
                    double lat1 = Double.parseDouble(bounds[0]);
                    double lon1 = Double.parseDouble(bounds[1]);
                    double lat2 = Double.parseDouble(bounds[2]);
                    double lon2 = Double.parseDouble(bounds[3]);

                    config.setBounds(lat1, lon1, lat2, lon2);
                    config.setSubscriptionType("bounds");
                } catch (NumberFormatException e) {
                    log.warn("Invalid bounds format: {}", params.get("bounds"));
                }
            }
        }
        // Подписка на маршруты
        else if (params.containsKey("routes")) {
            String[] routes = params.get("routes").split(",");
            config.setRouteFilter(Set.of(routes));
            config.setSubscriptionType("routes");
        }
        else {
            config.setSubscriptionType("all");
        }

        return config;
    }

    private Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new HashMap<>();
        if (query != null && !query.isEmpty()) {
            String[] pairs = query.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    params.put(keyValue[0], keyValue[1]);
                }
            }
        }
        return params;
    }

    private boolean isVehicleInScope(VehiclePositionDTO vehicle, SessionConfig config) {
        return switch (config.getSubscriptionType()) {
            case "routes" -> config.getRouteFilter() != null &&
                    config.getRouteFilter().contains(vehicle.getRouteNumber());
            case "bounds" -> config.isInBounds(
                    vehicle.getCurrentLatitude(),
                    vehicle.getCurrentLongitude()
            );
            default -> true;
        };
    }

    private boolean isPositionInScope(VehiclePositionWebSocketMessage position, SessionConfig config) {
        if (position == null || config == null) {
            return false;
        }

        return switch (config.getSubscriptionType()) {
            case "routes" -> config.getRouteFilter() != null &&
                    config.getRouteFilter().contains(position.getRouteNumber());
            case "bounds" -> config.isInBounds(
                    position.getLatitude(),
                    position.getLongitude()
            );
            default -> true;
        };
    }

    private VehiclePositionWebSocketMessage convertToWebSocketMessage(VehiclePositionDTO vehicle) {
        if (vehicle == null) {
            return null;
        }
        return new VehiclePositionWebSocketMessage(
                vehicle.getVehicleId(),
                vehicle.getLicensePlate(),
                vehicle.getRouteNumber(),
                vehicle.getCurrentLatitude(),
                vehicle.getCurrentLongitude(),
                vehicle.getSpeedKmh(),
                vehicle.getIsInMotion(),
                vehicle.getLastPositionUpdate() != null ?
                        vehicle.getLastPositionUpdate().toInstant(ZoneOffset.UTC) :
                        Instant.now()
        );
    }
}

