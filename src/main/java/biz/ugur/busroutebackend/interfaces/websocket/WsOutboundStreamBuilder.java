package biz.ugur.busroutebackend.interfaces.websocket;

import biz.ugur.busroutebackend.interfaces.websocket.dto.InitialPositionsChunk;
import biz.ugur.busroutebackend.interfaces.websocket.dto.InitialPositionsMessage;
import biz.ugur.busroutebackend.interfaces.websocket.dto.PositionUpdateMessage;
import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionDTO;
import biz.ugur.busroutebackend.transport.application.usecase.GetActiveVehiclesUseCase;
import biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer;
import biz.ugur.busroutebackend.transport.infrastructure.messaging.VehiclePositionWebSocketMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class WsOutboundStreamBuilder {

    private static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
    private static final Duration MAX_INITIAL_POSITION_AGE = Duration.ofMinutes(5);
    private static final int INITIAL_POSITIONS_LIMIT = 2000;
    private static final int INITIAL_POSITIONS_CHUNK_SIZE = 500;
    private static final int SUBSCRIPTION_CHANGE_LIMIT = 100;
    private static final int LIVE_BATCH_LIMIT = 1000;
    private static final Duration LIVE_BATCH_TIMEOUT = Duration.ofMillis(500);

    private final GetActiveVehiclesUseCase getActiveVehiclesUseCase;
    private final ObjectMapper objectMapper;
    private final WsBroadcastSink broadcastSink;
    private final PipelineTracer pipelineTracer;
    private final biz.ugur.busroutebackend.transport.application.services.VehicleCityIndex vehicleCityIndex;
    private final java.util.concurrent.atomic.AtomicInteger activeSessions =
            new java.util.concurrent.atomic.AtomicInteger();
    private java.util.function.Supplier<reactor.core.publisher.Flux<
            java.util.List<biz.ugur.busroutebackend.prediction.broadcast.V31FrameEnvelope>>>
            v31Frames = reactor.core.publisher.Flux::never;

    public void v31Frames(java.util.function.Supplier<reactor.core.publisher.Flux<
            java.util.List<biz.ugur.busroutebackend.prediction.broadcast.V31FrameEnvelope>>> frames) {
        this.v31Frames = frames;
    }

    public int activeSessions() {
        return activeSessions.get();
    }

    public WsOutboundStreamBuilder(GetActiveVehiclesUseCase getActiveVehiclesUseCase,
                                   ObjectMapper objectMapper,
                                   WsBroadcastSink broadcastSink,
                                   PipelineTracer pipelineTracer,
                                   biz.ugur.busroutebackend.transport.application.services.VehicleCityIndex vehicleCityIndex) {
        this.getActiveVehiclesUseCase = getActiveVehiclesUseCase;
        this.objectMapper = objectMapper;
        this.broadcastSink = broadcastSink;
        this.pipelineTracer = pipelineTracer;
        this.vehicleCityIndex = vehicleCityIndex;
    }

    public Flux<WebSocketMessage> buildFor(WebSocketSession session, SessionConfig config) {
        Flux<WebSocketMessage> initialPositions = initialPositionsStream(session, config);
        Flux<WebSocketMessage> liveUpdates = liveUpdatesStream(session, config);
        Flux<WebSocketMessage> heartbeat = heartbeatStream(session);

        Flux<WebSocketMessage> v31Live = v31Frames.get()
                .takeUntilOther(session.closeStatus().then())
                .map(batch -> batch.stream()
                        .filter(env -> isRouteInScope(env.routeNumber(), config)
                                && sessionCityAllows(env.vehicleId(), config))
                        .map(biz.ugur.busroutebackend.prediction.broadcast.V31FrameEnvelope::json)
                        .toList())
                .filter(jsons -> !jsons.isEmpty())
                .map(jsons -> session.textMessage(String.join("\n", jsons)));
        activeSessions.incrementAndGet();
        return Flux.merge(initialPositions, liveUpdates, heartbeat, v31Live)
                .doFinally(sig -> activeSessions.decrementAndGet())
                .onErrorContinue((error, obj) ->
                        log.error("Error sending WebSocket message - Error type: {}, Message: {}, Object type: {}",
                                error.getClass().getName(),
                                error.getMessage(),
                                obj != null ? obj.getClass().getName() : "null",
                                error));
    }

    public Mono<Void> sendCurrentPositions(WebSocketSession session, SessionConfig config) {
        return getVehiclesForConfig(config)
                .take(SUBSCRIPTION_CHANGE_LIMIT)
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

    private Flux<WebSocketMessage> heartbeatStream(WebSocketSession session) {
        return Flux.interval(HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL)
                .takeUntilOther(session.closeStatus().then())
                .map(tick -> session.pingMessage(factory -> factory.wrap(new byte[0])));
    }

    private Flux<WebSocketMessage> initialPositionsStream(WebSocketSession session, SessionConfig config) {
        if (config.isChunkedInitial()) {
            return chunkedInitialPositionsStream(session, config);
        }
        return getVehiclesForConfig(config)
                .take(INITIAL_POSITIONS_LIMIT)
                .map(this::convertToWebSocketMessage)
                .collectList()
                .map(positions -> {
                    try {
                        log.info("Sending {} initial positions for subscription type: {}, filter: {}",
                                positions.size(), config.getSubscriptionType(), config.getRouteFilter());
                        pipelineTracer.traceWsInitialSnapshotRead(
                                session.getId(),
                                config.getSubscriptionType(),
                                config.getRouteFilter(),
                                positions.size());
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

    private Flux<WebSocketMessage> chunkedInitialPositionsStream(WebSocketSession session, SessionConfig config) {
        return getVehiclesForConfig(config)
                .take(INITIAL_POSITIONS_LIMIT)
                .map(this::convertToWebSocketMessage)
                .buffer(INITIAL_POSITIONS_CHUNK_SIZE)
                .collectList()
                .flatMapMany(allChunks -> {
                    int totalChunks = allChunks.size();
                    int totalPositions = allChunks.stream().mapToInt(List::size).sum();
                    log.info("Sending {} initial positions in {} chunks of up to {} for subscription type: {}, filter: {}",
                            totalPositions, totalChunks, INITIAL_POSITIONS_CHUNK_SIZE,
                            config.getSubscriptionType(), config.getRouteFilter());
                    if (allChunks.isEmpty()) {
                        InitialPositionsChunk empty = InitialPositionsChunk.of(0L, true, List.of());
                        return Flux.just(serialiseChunk(session, empty));
                    }
                    return Flux.range(0, totalChunks)
                            .map(idx -> {
                                boolean last = idx == totalChunks - 1;
                                InitialPositionsChunk chunk = InitialPositionsChunk.of(
                                        idx.longValue(), last, allChunks.get(idx));
                                return serialiseChunk(session, chunk);
                            });
                })
                .doOnNext(msg -> log.debug("Sent initial positions chunk to session"));
    }

    private WebSocketMessage serialiseChunk(WebSocketSession session, InitialPositionsChunk chunk) {
        try {
            return session.textMessage(objectMapper.writeValueAsString(chunk));
        } catch (JsonProcessingException e) {
            log.error("Error serializing initial positions chunk page={}: {}",
                    chunk.page(), e.getMessage());
            return session.textMessage("{\"type\":\"error\",\"message\":\"Serialization error\"}");
        }
    }

    private boolean isRouteInScope(String routeNumber, SessionConfig config) {
        if (!"routes".equals(config.getSubscriptionType())) {
            return true;
        }
        return config.getRouteFilter() != null && config.getRouteFilter().contains(routeNumber);
    }

    boolean sessionCityAllows(String vehicleId, SessionConfig config) {
        String cityFilter = config.getCityFilter();
        if (cityFilter == null) {
            return true;
        }
        return vehicleCityIndex.cityOf(vehicleId)
                .map(cityFilter::equals)
                .orElse(true);
    }

    private Flux<WebSocketMessage> liveUpdatesStream(WebSocketSession session, SessionConfig config) {
        Mono<Void> closed = session.closeStatus().then();
        return broadcastSink.asFlux()
                .takeUntilOther(closed)
                .doOnNext(positionMsg -> {
                    if ("routes".equals(config.getSubscriptionType())) {
                        boolean inScope = isPositionInScope(positionMsg, config);
                        log.debug("Live update filter: sessionId={}, vehicle={}, vehicleRoute={}, subscribedRoutes={}, inScope={}",
                                session.getId(), positionMsg.getVehicleId(), positionMsg.getRouteNumber(),
                                config.getRouteFilter(), inScope);
                        if (!inScope) {
                            pipelineTracer.traceWsDroppedBySubscription(
                                    positionMsg.getVehicleId(),
                                    positionMsg.getLicensePlate(),
                                    positionMsg.getRouteNumber(),
                                    config.getSubscriptionType(),
                                    config.getRouteFilter());
                        }
                    }
                })
                .filter(positionMsg -> isPositionInScope(positionMsg, config))
                .bufferTimeout(LIVE_BATCH_LIMIT, LIVE_BATCH_TIMEOUT)
                .filter(updates -> !updates.isEmpty())
                .onBackpressureDrop(dropped ->
                        log.debug("Dropped batch of {} updates - session {} not consuming",
                                dropped.size(), session.getId()))
                .map(updates -> serialiseLiveBatch(session, config, updates))
                .onErrorContinue((error, obj) ->
                        log.warn("Error in live updates: {}", error.getMessage()));
    }

    private WebSocketMessage serialiseLiveBatch(WebSocketSession session,
                                                 SessionConfig config,
                                                 List<VehiclePositionWebSocketMessage> updates) {
        try {
            Map<String, VehiclePositionWebSocketMessage> latestUpdates = updates.stream()
                    .filter(msg -> msg != null && msg.getVehicleId() != null)
                    .collect(Collectors.toMap(
                            VehiclePositionWebSocketMessage::getVehicleId,
                            Function.identity(),
                            (existing, replacement) -> replacement));

            List<VehiclePositionWebSocketMessage> finalUpdates = new ArrayList<>(latestUpdates.values());

            log.debug("Batched {} updates into {} unique vehicles for subscription: {}",
                    updates.size(), finalUpdates.size(), config.getSubscriptionType());

            PositionUpdateMessage response = PositionUpdateMessage.of(finalUpdates);
            return session.textMessage(objectMapper.writeValueAsString(response));
        } catch (JsonProcessingException e) {
            log.warn("Error serializing batch position updates: {}", e.getMessage());
            return session.textMessage("{\"type\":\"error\",\"message\":\"Serialization error\"}");
        }
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
                            getActiveVehiclesUseCase.execute(
                                    GetActiveVehiclesUseCase.Query.byRoute(routeNumber, config.getCityFilter())))
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

    private boolean isPositionInScope(VehiclePositionWebSocketMessage position, SessionConfig config) {
        if (position == null) {
            return false;
        }
        if (config == null) {
            return true;
        }

        try {
            String subscriptionType = config.getSubscriptionType();
            if (subscriptionType == null) {
                return true;
            }

            return switch (subscriptionType) {
                case "routes" -> {
                    Set<String> routeFilter = config.getRouteFilter();
                    if (routeFilter == null || routeFilter.isEmpty()) {
                        yield false;
                    }
                    String routeNumber = position.getRouteNumber();
                    if (routeNumber == null || routeNumber.trim().isEmpty()) {
                        yield false;
                    }
                    yield routeFilter.contains(routeNumber.trim())
                            && sessionCityAllows(position.getVehicleId(), config);
                }
                case "bounds" -> {
                    Double lat = position.getLatitude();
                    Double lon = position.getLongitude();
                    if (lat == null || lon == null) {
                        log.warn("Vehicle {} has null coordinates: lat={}, lon={} - filtering out",
                                position.getVehicleId(), lat, lon);
                        yield false;
                    }
                    yield config.isInBounds(lat, lon);
                }
                default -> true;
            };
        } catch (Exception e) {
            log.error("Error in position scope check for vehicle {}: {}",
                    position.getVehicleId(), e.getMessage(), e);
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
                vehicle.getLastPositionUpdate() != null
                        ? vehicle.getLastPositionUpdate()
                        : LocalDateTime.now(),
                vehicle.getCourse(),
                vehicle.getLine());
    }
}
