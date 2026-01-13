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

import org.springframework.scheduling.annotation.Scheduled;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class VehiclePositionHandler implements WebSocketHandler {

    private static final int BUFFER_SIZE = 4096;

    private static final Duration SESSION_TIMEOUT = Duration.ofMinutes(5);

    private static final long CLEANUP_INTERVAL_MS = 60_000;

    private final GetActiveVehiclesUseCase getActiveVehiclesUseCase;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    private final WebSocketBufferMetricsTracker bufferMetrics;

    private final Map<String, SessionConfig> activeSessions = new ConcurrentHashMap<>();
    private final AtomicInteger sessionCounter = new AtomicInteger(0);
    private final AtomicLong totalExpiredSessions = new AtomicLong(0);


    private final Sinks.Many<VehiclePositionWebSocketMessage> broadcastSink =
            Sinks.many().multicast().onBackpressureBuffer(BUFFER_SIZE, false);

    public VehiclePositionHandler(GetActiveVehiclesUseCase getActiveVehiclesUseCase,
                                  ReactiveRedisTemplate<String, Object> redisTemplate,
                                  ObjectMapper objectMapper,
                                  WebSocketBufferMetricsTracker bufferMetrics) {
        this.getActiveVehiclesUseCase = getActiveVehiclesUseCase;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.bufferMetrics = bufferMetrics;

        subscribeToRedisUpdates();
    }


    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = generateSessionId();
        SessionConfig sessionConfig = parseSessionConfig(session);
        String clientIp = getClientIp(session);
        sessionConfig.setClientIp(clientIp);
        activeSessions.put(sessionId, sessionConfig);

        log.info("WebSocket connection established: {} from {} (total: {}, routes: {})",
                sessionId, clientIp, activeSessions.size(), sessionConfig.getRouteFilter());

        Flux<WebSocketMessage> outbound = createOutboundMessageStream(session, sessionConfig);

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
                    activeSessions.remove(sessionId);
                    log.info("WebSocket connection closed: {} from {} (total: {}, signal: {})",
                            sessionId, clientIp, activeSessions.size(), signalType);
                });
    }

    private String getClientIp(WebSocketSession session) {
        try {
            var remoteAddress = session.getHandshakeInfo().getRemoteAddress();
            if (remoteAddress != null) {
                return remoteAddress.getAddress().getHostAddress();
            }
        } catch (Exception e) {
            log.debug("Could not get client IP: {}", e.getMessage());
        }
        return "unknown";
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
        Flux<VehiclePositionDTO> vehiclesFlux = getVehiclesForConfig(config);

        return vehiclesFlux
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

    private Flux<VehiclePositionDTO> getVehiclesForConfig(SessionConfig config) {
        String subscriptionType = config.getSubscriptionType();

        if ("routes".equals(subscriptionType)) {
            Set<String> routes = config.getRouteFilter();
            if (routes == null || routes.isEmpty()) {
                return Flux.empty();
            }
            return Flux.fromIterable(routes)
                    .flatMap(routeNumber ->
                            getActiveVehiclesUseCase.execute(GetActiveVehiclesUseCase.Query.byRoute(routeNumber)))
                    .distinct(VehiclePositionDTO::getVehicleId);
        } else if ("bounds".equals(subscriptionType)) {
            return getActiveVehiclesUseCase.execute(null)
                    .filter(vehicle -> {
                        Double lat = vehicle.getCurrentLatitude();
                        Double lon = vehicle.getCurrentLongitude();
                        if (lat == null || lon == null) {
                            return false;
                        }
                        return config.isInBounds(lat, lon);
                    });
        } else {
            return getActiveVehiclesUseCase.execute(null);
        }
    }

    private Mono<Void> sendCurrentPositionsForConfig(WebSocketSession session, SessionConfig config) {
        return getVehiclesForConfig(config)
                .take(100)
                .map(this::convertToWebSocketMessage)
                .collectList()
                .flatMap(positions -> {
                    if (positions.isEmpty()) {
                        log.debug("No vehicles found for subscription: type={}, filter={}",
                                config.getSubscriptionType(), config.getRouteFilter());
                    }
                    try {
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
                    if ("routes".equals(config.getSubscriptionType())) {
                        log.debug("Live update filter: sessionId={}, vehicle={}, vehicleRoute={}, subscribedRoutes={}, inScope={}",
                                session.getId(), positionMsg.getVehicleId(), positionMsg.getRouteNumber(),
                                config.getRouteFilter(), inScope);
                    }
                })
                .filter(positionMsg -> isPositionInScope(positionMsg, config))
                .onBackpressureLatest()
                .bufferTimeout(100, Duration.ofMillis(500))
                .filter(updates -> !updates.isEmpty())
                .map(updates -> {
                    try {
                        Map<String, VehiclePositionWebSocketMessage> latestUpdates = updates.stream()
                                .filter(msg -> msg != null && msg.getVehicleId() != null)
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

            return switch (messageType) {
                case "ping" -> handlePing(sessionId, config);
                case "subscribe_routes" -> handleSubscribeRoutes(session, sessionId, config, clientMessage);
                case "subscribe_bounds" -> handleSubscribeBounds(session, sessionId, config, clientMessage);
                default -> {
                    log.debug("Unknown message type from session {}: {}", sessionId, messageType);
                    yield Mono.empty();
                }
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
                sessionId, oldFilter, message.getRoutes(), activeSessions.size());

        if (log.isDebugEnabled()) {
            activeSessions.forEach((sid, cfg) ->
                    log.debug("Active session {}: type={}, routes={}",
                            sid, cfg.getSubscriptionType(), cfg.getRouteFilter()));
        }

        return sendCurrentPositionsForConfig(session, config)
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

        return sendCurrentPositionsForConfig(session, config)
                .doOnSuccess(v -> log.debug("Sent updated positions for bounds change"))
                .doOnError(error -> log.warn("Error sending positions after bounds change: {}", error.getMessage()))
                .onErrorResume(e -> Mono.empty());
    }

    private void subscribeToRedisUpdates() {
        redisTemplate.listenToChannel("vehicle-position-updates")
                .filter(Objects::nonNull)
                .mapNotNull(message -> {
                    try {
                        Object messageObj = message.getMessage();

                        if (messageObj instanceof VehiclePositionWebSocketMessage) {
                            return (VehiclePositionWebSocketMessage) messageObj;
                        } else if (messageObj instanceof String) {
                            return objectMapper.readValue(
                                    (String) messageObj,
                                    VehiclePositionWebSocketMessage.class
                            );
                        } else {
                            return objectMapper.convertValue(
                                    messageObj,
                                    VehiclePositionWebSocketMessage.class
                            );
                        }
                    } catch (Exception e) {
                        message.getMessage();
                        log.warn("Error parsing Redis message of type {}: {}",
                                message.getMessage().getClass().getName(),
                                e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .filter(msg -> {
                    if (msg.getVehicleId() == null || msg.getVehicleId().isBlank()) {
                        log.warn("Filtered out WebSocket message with null/blank vehicleId: plate={}, route={}",
                                msg.getLicensePlate(), msg.getRouteNumber());
                        return false;
                    }
                    return true;
                })
                .subscribe(
                        positionMessage -> emitWithMetrics(positionMessage),
                        error -> log.error("Redis subscription error: {}", error.getMessage()),
                        () -> log.info("Redis subscription completed")
                );
    }


    private void emitWithMetrics(VehiclePositionWebSocketMessage message) {
        broadcastSink.emitNext(message, (signalType, emitResult) -> {
            if (emitResult == Sinks.EmitResult.OK) {
                bufferMetrics.recordEmitted();
                return false;
            }

            if (emitResult == Sinks.EmitResult.FAIL_NON_SERIALIZED) {
                return true;
            }

            if (emitResult == Sinks.EmitResult.FAIL_OVERFLOW) {
                bufferMetrics.recordDropped(message.getVehicleId());
                return false;
            }

            if (emitResult != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER
                    && emitResult != Sinks.EmitResult.FAIL_CANCELLED) {
                log.warn("Failed to emit position update for vehicle {}: {}",
                        message.getVehicleId(), emitResult);
            }
            return false;
        });
    }

    public void broadcastVehiclePosition(VehiclePositionWebSocketMessage message) {
        if (message == null) {
            log.warn("Cannot broadcast null WebSocket message");
            return;
        }

        emitWithMetrics(message);
    }

    public WebSocketBufferMetricsTracker.BufferHealthStats getBufferStats() {
        return bufferMetrics.getStats();
    }

    public boolean isBufferHealthy() {
        return bufferMetrics.isHealthy();
    }


    @Scheduled(fixedRate = CLEANUP_INTERVAL_MS)
    public void cleanupExpiredSessions() {
        if (activeSessions.isEmpty()) {
            return;
        }

        int beforeSize = activeSessions.size();
        List<String> expiredSessionIds = new ArrayList<>();

        activeSessions.forEach((sessionId, config) -> {
            if (config.isExpired(SESSION_TIMEOUT)) {
                expiredSessionIds.add(sessionId);
            }
        });

        if (expiredSessionIds.isEmpty()) {
            log.debug("Session cleanup: no expired sessions found (active: {})", beforeSize);
            return;
        }

        for (String sessionId : expiredSessionIds) {
            SessionConfig config = activeSessions.remove(sessionId);
            if (config != null) {
                totalExpiredSessions.incrementAndGet();
                log.info("WEBSOCKET_SESSION_EXPIRED: Removed stale session {} from {} " +
                         "(inactive for {}, subscription: {})",
                        sessionId,
                        config.getClientIp(),
                        formatDuration(config.getInactiveDuration()),
                        config.getSubscriptionType());
            }
        }

        log.warn("WEBSOCKET_SESSION_CLEANUP: Removed {} expired sessions (was: {}, now: {}, total expired: {})",
                expiredSessionIds.size(), beforeSize, activeSessions.size(), totalExpiredSessions.get());
    }

    public long getTotalExpiredSessions() {
        return totalExpiredSessions.get();
    }

    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else {
            return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
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
            case "routes" -> {
                String routeNumber = vehicle.getRouteNumber();
                if (routeNumber == null || routeNumber.isBlank()) {
                    yield false;
                }
                yield config.getRouteFilter() != null &&
                        config.getRouteFilter().contains(routeNumber);
            }
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
                    position.getVehicleId(),
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
                vehicle.getCourse(),
                vehicle.getLine()
        );
    }
}

