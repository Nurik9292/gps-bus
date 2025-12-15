package biz.ugur.busroutebackend.transport.infrastructure.messaging;

import biz.ugur.busroutebackend.interfaces.websocket.VehiclePositionHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class VehiclePositionWebSocketPublisher {

    private final VehiclePositionHandler vehiclePositionHandler;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    public VehiclePositionWebSocketPublisher(VehiclePositionHandler vehiclePositionHandler,
                                             ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.vehiclePositionHandler = vehiclePositionHandler;
        this.redisTemplate = redisTemplate;
    }
    public Mono<Void> broadcastVehiclePosition(VehiclePositionWebSocketMessage message) {
        Mono<Void> localBroadcast = Mono.fromRunnable(() ->
            vehiclePositionHandler.broadcastVehiclePosition(message)
        );

        Mono<Void> redisPublish = redisTemplate.convertAndSend("vehicle-position-updates", message)
                .doOnError(error -> log.warn("Failed to publish to Redis channel: {}", error.getMessage()))
                .onErrorResume(error -> {
                    log.debug("Continuing after Redis publish error", error);
                    return Mono.empty();
                })
                .then();

        return localBroadcast.then(redisPublish);
    }

    public Mono<Void> broadcastRouteAssignment(VehicleRouteAssignmentMessage message) {
        log.debug("Broadcasting route assignment: {}", message.getVehicleId());

        // TODO: Отправка через WebSocket
        // messagingTemplate.convertAndSend("/topic/route-assignments", message);

        return Mono.empty();
    }
}
