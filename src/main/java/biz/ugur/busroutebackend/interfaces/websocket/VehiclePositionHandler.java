package biz.ugur.busroutebackend.interfaces.websocket;

import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePositionPredictionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class VehiclePositionHandler implements WebSocketHandler {

    private final VehiclePositionPredictionService predictionService;
    private final WsSessionRegistry sessionRegistry;
    private final WsOutboundStreamBuilder outboundBuilder;
    private final WsInboundMessageDispatcher inboundDispatcher;

    public VehiclePositionHandler(@org.springframework.context.annotation.Lazy VehiclePositionPredictionService predictionService,
                                  WsSessionRegistry sessionRegistry,
                                  WsOutboundStreamBuilder outboundBuilder,
                                  WsInboundMessageDispatcher inboundDispatcher) {
        this.predictionService = predictionService;
        this.sessionRegistry = sessionRegistry;
        this.outboundBuilder = outboundBuilder;
        this.inboundDispatcher = inboundDispatcher;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = sessionRegistry.register(session);
        SessionConfig sessionConfig = sessionRegistry.get(sessionId).orElseThrow();

        log.info("WebSocket connection established: {} from {} (total: {}, routes: {})",
                sessionId, sessionConfig.getClientIp(), sessionRegistry.activeCount(), sessionConfig.getRouteFilter());

        Flux<WebSocketMessage> outbound = outboundBuilder.buildFor(session, sessionConfig);

        Mono<Void> inbound = session.receive()
                .flatMap(message -> inboundDispatcher.dispatch(session, sessionId, sessionConfig, message))
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
}
