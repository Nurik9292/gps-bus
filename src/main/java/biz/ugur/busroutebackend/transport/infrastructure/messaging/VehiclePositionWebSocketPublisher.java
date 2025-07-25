package biz.ugur.busroutebackend.transport.infrastructure.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class VehiclePositionWebSocketPublisher {

    // TODO: Реализовать WebSocket handler
    // private final SimpMessagingTemplate messagingTemplate;

    public Mono<Void> broadcastVehiclePosition(VehiclePositionWebSocketMessage message) {
        log.debug("Broadcasting vehicle position: {}", message.getVehicleId());

        // TODO: Отправка через WebSocket
        // messagingTemplate.convertAndSend("/topic/vehicle-positions", message);

        return Mono.empty();
    }

    public Mono<Void> broadcastRouteAssignment(VehicleRouteAssignmentMessage message) {
        log.debug("Broadcasting route assignment: {}", message.getVehicleId());

        // TODO: Отправка через WebSocket
        // messagingTemplate.convertAndSend("/topic/route-assignments", message);

        return Mono.empty();
    }
}
