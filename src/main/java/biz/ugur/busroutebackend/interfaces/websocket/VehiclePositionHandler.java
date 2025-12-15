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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class VehiclePositionHandler implements WebSocketHandler {

    private final GetActiveVehiclesUseCase getActiveVehiclesUseCase;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private final Map<String, SessionConfig> activeSessions = new ConcurrentHashMap<>();
    private final AtomicInteger sessionCounter = new AtomicInteger(0);

    private final Sinks.Many<VehiclePositionWebSocketMessage> broadcastSink =
            Sinks.many().multicast().directBestEffort();

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
                .doOnNext(message -> handleIncomingMessage(session, sessionId, sessionConfig, message))
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

        return Flux.merge(initialPositions, liveUpdates)
                .onErrorContinue((error, obj) -> {
                    log.error("Error sending WebSocket message - Error type: {}, Message: {}, Object type: {}",
                            error.getClass().getName(),
                            error.getMessage(),
                            obj != null ? obj.getClass().getName() : "null",
                            error);
                });
    }

    private Flux<WebSocketMessage> getInitialPositions(WebSocketSession session, SessionConfig config) {
        return getActiveVehiclesUseCase.execute(null)
                .doOnNext(vehicle -> log.debug("Checking vehicle for initial positions: plate={}, route={}, inScope={}",
                        vehicle.getLicensePlate(), vehicle.getRouteNumber(), isVehicleInScope(vehicle, config)))
                .filter(vehicle -> isVehicleInScope(vehicle, config))
                .take(100)
                .map(this::convertToWebSocketMessage)
                .collectList()
                .map(positions -> {
                    try {
                        log.info("Sending {} initial positions for subscription type: {}, filter: {}",
                                positions.size(), config.getSubscriptionType(), config.getRouteFilter());
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

    /**
     * Отправляет текущие позиции транспортных средств для заданной конфигурации подписки.
     * Используется при смене маршрута или границ клиентом.
     */
    private Mono<Void> sendCurrentPositionsForConfig(WebSocketSession session, SessionConfig config) {
        return getActiveVehiclesUseCase.execute(null)
                .filter(vehicle -> isVehicleInScope(vehicle, config))
                .take(100)
                .map(this::convertToWebSocketMessage)
                .collectList()
                .flatMap(positions -> {
                    if (positions.isEmpty()) {
                        log.debug("No vehicles found for subscription: type={}, filter={}",
                                config.getSubscriptionType(), config.getRouteFilter());
                    }
                    try {
                        // Используем тот же формат что и initial_positions для совместимости с клиентом
                        Map<String, Object> response = Map.of(
                                "type", "initial_positions",
                                "count", positions.size(),
                                "vehicles", positions,
                                "timestamp", Instant.now().toString()
                        );
                        String json = objectMapper.writeValueAsString(response);
                        log.info("Sending {} positions for subscription change: type={}, filter={}",
                                positions.size(), config.getSubscriptionType(), config.getRouteFilter());
                        return session.send(Mono.just(session.textMessage(json)));
                    } catch (JsonProcessingException e) {
                        log.error("Error serializing subscription update positions: {}", e.getMessage());
                        return Mono.empty();
                    }
                });
    }

    private Flux<WebSocketMessage> getLivePositionUpdates(WebSocketSession session,
                                                          SessionConfig config) {
        return broadcastSink.asFlux()
                .doOnNext(positionMsg -> {
                    boolean inScope = isPositionInScope(positionMsg, config);
                    if (log.isDebugEnabled()) {
                        log.debug("Live update filter: vehicle={}, route={}, subscriptionType={}, routeFilter={}, inScope={}",
                                positionMsg.getVehicleId(), positionMsg.getRouteNumber(),
                                config.getSubscriptionType(), config.getRouteFilter(), inScope);
                    }
                })
                .filter(positionMsg -> isPositionInScope(positionMsg, config))
                .buffer(Duration.ofMillis(500))
                .filter(updates -> !updates.isEmpty())
                .map(updates -> {
                    try {
                        Map<String, VehiclePositionWebSocketMessage> latestUpdates = updates.stream()
                                .collect(Collectors.toMap(
                                        VehiclePositionWebSocketMessage::getVehicleId,
                                        Function.identity(),
                                        (existing, replacement) -> replacement
                                ));

                        List<VehiclePositionWebSocketMessage> finalUpdates = new ArrayList<>(latestUpdates.values());

                        log.debug("Batched {} updates into {} unique vehicles for subscription: {}",
                                updates.size(), finalUpdates.size(), config.getSubscriptionType());

                        Map<String, Object> response = Map.of(
                                "type", "position_update",
                                "count", finalUpdates.size(),
                                "vehicles", finalUpdates,
                                "timestamp", Instant.now().toString()
                        );

                        return session.textMessage(objectMapper.writeValueAsString(response));

                    } catch (JsonProcessingException e) {
                        log.warn("Error serializing batch position updates: {}", e.getMessage());
                        return session.textMessage("{\"type\":\"error\",\"message\":\"Serialization error\"}");
                    }
                })
                .onErrorContinue((error, obj) ->
                        log.warn("Error in live updates: {}", error.getMessage()));
    }


    private void handleIncomingMessage(WebSocketSession session, String sessionId,
                                        SessionConfig config, WebSocketMessage message) {
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
                        log.info("Session {} updated route subscription: {}", sessionId, routes);

                        // Отправляем текущие позиции для нового маршрута
                        sendCurrentPositionsForConfig(session, config)
                                .subscribe(
                                        msg -> log.debug("Sent updated positions for route change"),
                                        error -> log.warn("Error sending positions after route change: {}",
                                                error.getMessage())
                                );
                    }
                    break;

                case "subscribe_bounds":
                    @SuppressWarnings("unchecked")
                    java.util.List<Double> bounds = (List<Double>) clientMessage.get("bounds");
                    if (bounds != null && bounds.size() == 4) {
                        config.setBounds(bounds.get(0), bounds.get(1), bounds.get(2), bounds.get(3));
                        config.setSubscriptionType("bounds");
                        log.info("Session {} updated bounds subscription", sessionId);

                        // Отправляем текущие позиции для новых границ
                        sendCurrentPositionsForConfig(session, config)
                                .subscribe(
                                        msg -> log.debug("Sent updated positions for bounds change"),
                                        error -> log.warn("Error sending positions after bounds change: {}",
                                                error.getMessage())
                                );
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
        redisTemplate.listenToChannel("vehicle-position-updates")
                .filter(Objects::nonNull)
                .mapNotNull(message -> {
                    try {
                        Object messageObj = message.getMessage();

                        // Handle different message types from Redis
                        if (messageObj instanceof VehiclePositionWebSocketMessage) {
                            // Already deserialized by Redis
                            return (VehiclePositionWebSocketMessage) messageObj;
                        } else if (messageObj instanceof String) {
                            // JSON string - parse it
                            return objectMapper.readValue(
                                    (String) messageObj,
                                    VehiclePositionWebSocketMessage.class
                            );
                        } else {
                            // Try to convert via Jackson (handles Map, ArrayList, etc.)
                            return objectMapper.convertValue(
                                    messageObj,
                                    VehiclePositionWebSocketMessage.class
                            );
                        }
                    } catch (Exception e) {
                        log.warn("Error parsing Redis message of type {}: {}",
                                message.getMessage() != null ? message.getMessage().getClass().getName() : "null",
                                e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .subscribe(
                        positionMessage -> {
                            broadcastSink.emitNext(positionMessage, (signalType, emitResult) -> {
                                if (emitResult == Sinks.EmitResult.FAIL_NON_SERIALIZED) {
                                    return true; // retry
                                }
                                // FAIL_ZERO_SUBSCRIBER and FAIL_CANCELLED are expected when no WebSocket clients are connected
                                if (emitResult != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER
                                        && emitResult != Sinks.EmitResult.FAIL_CANCELLED) {
                                    log.warn("Failed to emit position update: {}", emitResult);
                                }
                                return false;
                            });
                        },
                        error -> log.error("Redis subscription error: {}", error.getMessage()),
                        () -> log.info("Redis subscription completed")
                );
    }

    public void broadcastVehiclePosition(VehiclePositionWebSocketMessage message) {
        if (message == null) {
            log.warn("Cannot broadcast null WebSocket message");
            return;
        }

        broadcastSink.emitNext(message, (signalType, emitResult) -> {
            if (emitResult == Sinks.EmitResult.FAIL_NON_SERIALIZED) {
                return true; // retry on concurrent access
            }
            // FAIL_ZERO_SUBSCRIBER and FAIL_CANCELLED are expected when no WebSocket clients are connected
            if (emitResult != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER
                    && emitResult != Sinks.EmitResult.FAIL_CANCELLED) {
                log.warn("Failed to broadcast vehicle position: {}", emitResult);
            }
            return false;
        });
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
                    totalSessions,
                    subscriptionTypes,
                    LocalDateTime.now()
            );
        });
    }

    private String generateSessionId() {
        return "ws-" + sessionCounter.incrementAndGet() + "-" + System.currentTimeMillis();
    }

    private SessionConfig parseSessionConfig(WebSocketSession session) {
        SessionConfig config = new SessionConfig();

        String query = session.getHandshakeInfo().getUri().getQuery();
        String uri = session.getHandshakeInfo().getUri().toString();
        log.info("WebSocket connection URI: {}", uri);
        log.info("WebSocket query string: {}", query);

        if (query == null) {
            config.setSubscriptionType("all");
            log.info("No query params - subscription type: all");
            return config;
        }

        Map<String, String> params = parseQueryString(query);
        log.info("Parsed query params: {}", params);

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
                    log.info("Configured bounds subscription: {}", config.getBoundsString());
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
            log.info("Configured routes subscription: routes={}", config.getRouteFilter());
        }
        else {
            config.setSubscriptionType("all");
            log.info("No specific filter - subscription type: all");
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
        if (position == null) {
            log.trace("Position message is null, filtering out");
            return false;
        }

        if (config == null) {
            log.trace("Session config is null, allowing all positions");
            return true;
        }

        try {
            String subscriptionType = config.getSubscriptionType();
            if (subscriptionType == null) {
                log.trace("Subscription type is null, allowing all");
                return true;
            }

            return switch (subscriptionType) {
                case "routes" -> {
                    Set<String> routeFilter = config.getRouteFilter();
                    if (routeFilter == null || routeFilter.isEmpty()) {
                        log.trace("No route filter configured, filtering out");
                        yield false;
                    }

                    String routeNumber = position.getRouteNumber();

                    if (routeNumber == null || routeNumber.trim().isEmpty()) {
                        log.trace("Vehicle {} has no route assignment, filtering out for route subscription",
                                position.getVehicleId());
                        yield false;
                    }

                    boolean inScope = routeFilter.contains(routeNumber.trim());
                    log.trace("Route filter check for vehicle {}: route={}, inScope={}",
                            position.getVehicleId(), routeNumber, inScope);
                    yield inScope;
                }
                case "bounds" -> {
                    Double lat = position.getLatitude();
                    Double lon = position.getLongitude();

                    if (lat == null || lon == null) {
                        log.warn("Vehicle {} has null coordinates: lat={}, lon={} - filtering out",
                                position.getVehicleId(), lat, lon);
                        yield false;
                    }

                    boolean inBounds = config.isInBounds(lat, lon);
                    log.trace("Bounds check for vehicle {}: ({}, {}), inBounds={}",
                            position.getVehicleId(),
                            String.format("%.6f", lat),
                            String.format("%.6f", lon),
                            inBounds);
                    yield inBounds;
                }
                default -> {
                    log.trace("Default subscription type '{}': allowing all", subscriptionType);
                    yield true;
                }
            };
        } catch (Exception e) {
            log.error("Error in position scope check for vehicle {}: {}",
                    position != null ? position.getVehicleId() : "null",
                    e.getMessage(), e);
            return false;
        }
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
                        vehicle.getLastPositionUpdate() :
                        LocalDateTime.now(),
                vehicle.getCourse()
        );
    }
}

