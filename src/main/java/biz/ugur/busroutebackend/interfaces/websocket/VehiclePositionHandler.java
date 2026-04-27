package biz.ugur.busroutebackend.interfaces.websocket;

import biz.ugur.busroutebackend.interfaces.websocket.dto.InitialPositionsMessage;
import biz.ugur.busroutebackend.interfaces.websocket.dto.PositionUpdateMessage;
import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionDTO;
import biz.ugur.busroutebackend.transport.application.usecase.GetActiveVehiclesUseCase;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePositionPredictionService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class VehiclePositionHandler implements WebSocketHandler {

    private static final Duration MAX_INITIAL_POSITION_AGE = Duration.ofMinutes(5);
    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);

    private final GetActiveVehiclesUseCase getActiveVehiclesUseCase;
    private final ObjectMapper objectMapper;
    private final VehiclePositionPredictionService predictionService;
    private final WsSessionRegistry sessionRegistry;
    private final WsBroadcastSink broadcastSink;

    public VehiclePositionHandler(GetActiveVehiclesUseCase getActiveVehiclesUseCase,
                                  ObjectMapper objectMapper,
                                  @org.springframework.context.annotation.Lazy VehiclePositionPredictionService predictionService,
                                  WsSessionRegistry sessionRegistry,
                                  WsBroadcastSink broadcastSink) {
        this.getActiveVehiclesUseCase = getActiveVehiclesUseCase;
        this.objectMapper = objectMapper;
        this.predictionService = predictionService;
        this.sessionRegistry = sessionRegistry;
        this.broadcastSink = broadcastSink;
    }



    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = sessionRegistry.register(session);
        SessionConfig sessionConfig = sessionRegistry.get(sessionId).orElseThrow();

        log.info("WebSocket connection established: {} from {} (total: {}, routes: {})",
                sessionId, sessionConfig.getClientIp(), sessionRegistry.activeCount(), sessionConfig.getRouteFilter());

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
                    sessionRegistry.remove(sessionId);
                    log.info("WebSocket connection closed: {} from {} (total: {}, signal: {})",
                            sessionId, sessionConfig.getClientIp(), sessionRegistry.activeCount(), signalType);
                });
    }

    private Flux<WebSocketMessage> createOutboundMessageStream(WebSocketSession session,
                                                               SessionConfig config) {

        Flux<WebSocketMessage> initialPositions = getInitialPositions(session, config);
        Flux<WebSocketMessage> liveUpdates = getLivePositionUpdates(session, config);
        Flux<WebSocketMessage> heartbeat = heartbeatStream(session);

        return Flux.merge(initialPositions, liveUpdates, heartbeat)
                .onErrorContinue((error, obj) -> {
                    log.error("Error sending WebSocket message - Error type: {}, Message: {}, Object type: {}",
                            error.getClass().getName(),
                            error.getMessage(),
                            obj != null ? obj.getClass().getName() : "null",
                            error);
                });
    }

    private Flux<WebSocketMessage> heartbeatStream(WebSocketSession session) {
        return Flux.interval(HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL)
                .takeUntilOther(session.closeStatus().then())
                .map(tick -> session.pingMessage(factory -> factory.wrap(new byte[0])));
    }

    private Flux<WebSocketMessage> getInitialPositions(WebSocketSession session, SessionConfig config) {
        Flux<VehiclePositionDTO> vehiclesFlux = getVehiclesForConfig(config);

        return vehiclesFlux
                .take(2000)
                .map(this::convertToWebSocketMessage)
                .collectList()
                .map(positions -> {
                    try {
                        log.info("Sending {} initial positions for subscription type: {}, filter: {}",
                                positions.size(), config.getSubscriptionType(), config.getRouteFilter());
                        InitialPositionsMessage response = InitialPositionsMessage.of(positions);
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

        Flux<VehiclePositionDTO> result;
        if ("routes".equals(subscriptionType)) {
            Set<String> routes = config.getRouteFilter();
            if (routes == null || routes.isEmpty()) {
                return Flux.empty();
            }
            result = Flux.fromIterable(routes)
                    .flatMap(routeNumber ->
                            getActiveVehiclesUseCase.execute(GetActiveVehiclesUseCase.Query.byRoute(routeNumber)))
                    .distinct(VehiclePositionDTO::getVehicleId);
        } else if ("bounds".equals(subscriptionType)) {
            result = getActiveVehiclesUseCase.execute(null)
                    .filter(vehicle -> {
                        Double lat = vehicle.getCurrentLatitude();
                        Double lon = vehicle.getCurrentLongitude();
                        if (lat == null || lon == null) {
                            return false;
                        }
                        return config.isInBounds(lat, lon);
                    });
        } else {
            result = getActiveVehiclesUseCase.execute(null);
        }

        LocalDateTime freshnessCutoff = LocalDateTime.now(ZoneOffset.UTC).minus(MAX_INITIAL_POSITION_AGE);
        return result
                .filter(v -> v.getRouteNumber() != null && !v.getRouteNumber().isBlank())
                .filter(v -> v.getLastPositionUpdate() != null
                        && v.getLastPositionUpdate().isAfter(freshnessCutoff));
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
                        InitialPositionsMessage response = InitialPositionsMessage.of(positions);
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
        Mono<Void> closed = session.closeStatus().then();
        return broadcastSink.asFlux()
                .takeUntilOther(closed)
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
                .bufferTimeout(1000, Duration.ofMillis(500))
                .takeUntilOther(closed)
                .filter(updates -> !updates.isEmpty())
                .onBackpressureDrop(dropped ->
                        log.debug("Dropped batch of {} updates - session {} not consuming",
                                dropped.size(), session.getId()))
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

                        PositionUpdateMessage response = PositionUpdateMessage.of(finalUpdates);

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
