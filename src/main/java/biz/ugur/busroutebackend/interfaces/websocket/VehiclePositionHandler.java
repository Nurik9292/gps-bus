package biz.ugur.busroutebackend.interfaces.websocket;

import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionDTO;
import biz.ugur.busroutebackend.transport.application.usecase.GetActiveVehiclesUseCase;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class VehiclePositionHandler extends TextWebSocketHandler {

    private final GetActiveVehiclesUseCase  getActiveVehiclesUseCase;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private final Map<String, WebSocketSessionWrapper> activeSessions = new ConcurrentHashMap<>();
    private final AtomicInteger sessionCounter = new AtomicInteger(0);

    public VehiclePositionHandler(GetActiveVehiclesUseCase getActiveVehiclesUseCase,
                                  ReactiveRedisTemplate<String, Object> redisTemplate,
                                  ObjectMapper objectMapper) {
        this.getActiveVehiclesUseCase = getActiveVehiclesUseCase;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = generateSessionId();
        WebSocketSessionWrapper sessionWrapper = new WebSocketSessionWrapper(sessionId, session);

        // Парсим параметры подписки из URL
        parseSubscriptionParameters(sessionWrapper);

        activeSessions.put(sessionId, sessionWrapper);

        log.info("WebSocket connection established: {} (total: {})",
                sessionId, activeSessions.size());

        // Отправляем текущие позиции при подключении
        sendInitialVehiclePositions(sessionWrapper);

        // Подписываемся на обновления
        subscribeToPositionUpdates(sessionWrapper);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = findSessionId(session);
        if (sessionId != null) {
            WebSocketSessionWrapper sessionWrapper = activeSessions.remove(sessionId);
            if (sessionWrapper != null) {
                sessionWrapper.dispose();
            }

            log.info("WebSocket connection closed: {} (total: {}, status: {})",
                    sessionId, activeSessions.size(), status);
        }
    }

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            String sessionId = findSessionId(session);
            WebSocketSessionWrapper sessionWrapper = activeSessions.get(sessionId);

            if (sessionWrapper != null) {
                handleClientMessage(sessionWrapper, message.getPayload());
            }
        } catch (Exception e) {
            log.warn("Error handling WebSocket message: {}", e.getMessage());
            sendErrorMessage(session, "Invalid message format");
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String sessionId = findSessionId(session);
        log.error("WebSocket transport error for session {}: {}", sessionId, exception.getMessage());

        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    /**
     * Broadcast обновления позиций всем подключенным клиентам
     */
    public void broadcastVehiclePosition(VehiclePositionWebSocketMessage message) {
        if (activeSessions.isEmpty()) {
            return;
        }

        activeSessions.values().parallelStream()
                .filter(sessionWrapper -> sessionWrapper.isInterestedInVehicle(message))
                .forEach(sessionWrapper -> {
                    try {
                        sendVehiclePositionToSession(sessionWrapper, message);
                    } catch (Exception e) {
                        log.warn("Failed to send position update to session {}: {}",
                                sessionWrapper.getSessionId(), e.getMessage());
                    }
                });
    }

    /**
     * Получить статистику активных соединений
     */
    public Mono<WebSocketStatsDTO> getConnectionStats() {
        return Mono.fromCallable(() -> {
            int totalSessions = activeSessions.size();
            long activeSessions = this.activeSessions.values().stream()
                    .filter(WebSocketSessionWrapper::isActive)
                    .count();

            Map<String, Long> subscriptionTypes = this.activeSessions.values().stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            WebSocketSessionWrapper::getSubscriptionType,
                            java.util.stream.Collectors.counting()
                    ));

            return new WebSocketStatsDTO(
                    totalSessions,
                    (int) activeSessions,
                    subscriptionTypes,
                    Instant.now()
            );
        });
    }



    private String generateSessionId() {
        return "ws-" + sessionCounter.incrementAndGet() + "-" + System.currentTimeMillis();
    }

    private String findSessionId(WebSocketSession session) {
        return activeSessions.entrySet().stream()
                .filter(entry -> entry.getValue().getSession().equals(session))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private void parseSubscriptionParameters(WebSocketSessionWrapper sessionWrapper) {
        URI uri = sessionWrapper.getSession().getUri();
        if (uri == null) return;

        String query = uri.getQuery();
        if (query == null) {
            sessionWrapper.setSubscriptionType("all");
            return;
        }

        Map<String, String> params = parseQueryString(query);

        // Подписка на область
        if (params.containsKey("bounds")) {
            String[] bounds = params.get("bounds").split(",");
            if (bounds.length == 4) {
                try {
                    double lat1 = Double.parseDouble(bounds[0]);
                    double lon1 = Double.parseDouble(bounds[1]);
                    double lat2 = Double.parseDouble(bounds[2]);
                    double lon2 = Double.parseDouble(bounds[3]);

                    sessionWrapper.setBounds(lat1, lon1, lat2, lon2);
                    sessionWrapper.setSubscriptionType("bounds");

                    log.debug("Session {} subscribed to bounds: ({},{}) to ({},{})",
                            sessionWrapper.getSessionId(), lat1, lon1, lat2, lon2);
                } catch (NumberFormatException e) {
                    log.warn("Invalid bounds format for session {}: {}",
                            sessionWrapper.getSessionId(), params.get("bounds"));
                }
            }
        }

        else if (params.containsKey("routes")) {
            String[] routes = params.get("routes").split(",");
            sessionWrapper.setRouteFilter(java.util.Set.of(routes));
            sessionWrapper.setSubscriptionType("routes");

            log.debug("Session {} subscribed to routes: {}",
                    sessionWrapper.getSessionId(), java.util.Arrays.toString(routes));
        }

        else {
            sessionWrapper.setSubscriptionType("all");
        }
    }

    private Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new java.util.HashMap<>();
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

    private void sendInitialVehiclePositions(WebSocketSessionWrapper sessionWrapper) {
        getActiveVehiclesUseCase.execute(null)
                .filter(sessionWrapper::isInterestedInVehicle)
                .take(100) // Ограничиваем количество для начальной загрузки
                .map(this::convertToWebSocketMessage)
                .collectList()
                .subscribe(
                        positions -> {
                            try {
                                String message = objectMapper.writeValueAsString(Map.of(
                                        "type", "initial_positions",
                                        "count", positions.size(),
                                        "vehicles", positions
                                ));
                                sessionWrapper.sendMessage(new TextMessage(message));

                                log.debug("Sent {} initial positions to session {}",
                                        positions.size(), sessionWrapper.getSessionId());
                            } catch (Exception e) {
                                log.error("Failed to send initial positions to session {}: {}",
                                        sessionWrapper.getSessionId(), e.getMessage());
                            }
                        },
                        error -> log.error("Error loading initial positions for session {}: {}",
                                sessionWrapper.getSessionId(), error.getMessage())
                );
    }

    private void subscribeToPositionUpdates(WebSocketSessionWrapper sessionWrapper) {
        // Подписываемся на Redis Pub/Sub для обновлений позиций
        Flux<String> redisStream = redisTemplate.listenToChannel("vehicle-position-updates")
                .map(message -> message.getMessage().toString());

        sessionWrapper.setUpdateSubscription(
                redisStream
                        .delayElements(Duration.ofMillis(100))
                        .subscribe(
                                messageJson -> handlePositionUpdate(sessionWrapper, messageJson),
                                error -> {
                                    log.error("Redis subscription error for session {}: {}",
                                            sessionWrapper.getSessionId(), error.getMessage());
                                    sessionWrapper.dispose();
                                }
                        )
        );
    }

    private void handlePositionUpdate(WebSocketSessionWrapper sessionWrapper, String messageJson) {
        try {
            VehiclePositionWebSocketMessage positionMessage =
                    objectMapper.readValue(messageJson, VehiclePositionWebSocketMessage.class);

            if (sessionWrapper.isInterestedInVehicle(positionMessage)) {
                sendVehiclePositionToSession(sessionWrapper, positionMessage);
            }
        } catch (Exception e) {
            log.warn("Failed to process position update for session {}: {}",
                    sessionWrapper.getSessionId(), e.getMessage());
        }
    }

    private void sendVehiclePositionToSession(WebSocketSessionWrapper sessionWrapper,
                                              VehiclePositionWebSocketMessage message) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "type", "position_update",
                    "vehicle", message
            ));

            sessionWrapper.sendMessage(new TextMessage(json));
            sessionWrapper.incrementMessageCount();

        } catch (Exception e) {
            log.warn("Failed to send position to session {}: {}",
                    sessionWrapper.getSessionId(), e.getMessage());
        }
    }

    private void handleClientMessage(WebSocketSessionWrapper sessionWrapper, String messagePayload) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> clientMessage = objectMapper.readValue(messagePayload, Map.class);

            String messageType = (String) clientMessage.get("type");

            switch (messageType) {
                case "ping":
                    sendPongMessage(sessionWrapper);
                    break;
                case "subscribe_routes":
                    @SuppressWarnings("unchecked")
                    java.util.List<String> routes = (java.util.List<String>) clientMessage.get("routes");
                    if (routes != null) {
                        sessionWrapper.setRouteFilter(new java.util.HashSet<>(routes));
                        sessionWrapper.setSubscriptionType("routes");
                        log.debug("Session {} updated route subscription: {}",
                                sessionWrapper.getSessionId(), routes);
                    }
                    break;
                case "subscribe_bounds":
                    @SuppressWarnings("unchecked")
                    java.util.List<Double> bounds = (java.util.List<Double>) clientMessage.get("bounds");
                    if (bounds != null && bounds.size() == 4) {
                        sessionWrapper.setBounds(bounds.get(0), bounds.get(1), bounds.get(2), bounds.get(3));
                        sessionWrapper.setSubscriptionType("bounds");
                        log.debug("Session {} updated bounds subscription", sessionWrapper.getSessionId());
                    }
                    break;
                default:
                    log.debug("Unknown message type from session {}: {}",
                            sessionWrapper.getSessionId(), messageType);
            }
        } catch (Exception e) {
            log.warn("Failed to handle client message from session {}: {}",
                    sessionWrapper.getSessionId(), e.getMessage());
        }
    }

    private void sendPongMessage(WebSocketSessionWrapper sessionWrapper) {
        try {
            String pongMessage = objectMapper.writeValueAsString(Map.of(
                    "type", "pong",
                    "timestamp", Instant.now().toString()
            ));
            sessionWrapper.sendMessage(new TextMessage(pongMessage));
        } catch (Exception e) {
            log.warn("Failed to send pong to session {}: {}",
                    sessionWrapper.getSessionId(), e.getMessage());
        }
    }

    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "type", "error",
                    "message", errorMessage,
                    "timestamp", Instant.now().toString()
            ));
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.error("Failed to send error message: {}", e.getMessage());
        }
    }

    private VehiclePositionWebSocketMessage convertToWebSocketMessage(VehiclePositionDTO vehicle) {
        return new VehiclePositionWebSocketMessage(
                vehicle.getVehicleId(),
                vehicle.getLicensePlate(),
                vehicle.getCurrentLatitude(),
                vehicle.getCurrentLongitude(),
                vehicle.getSpeedKmh(),
                vehicle.getIsInMotion(),
                vehicle.getLastPositionUpdate().toInstant(ZoneOffset.UTC)
        );
    }
}
