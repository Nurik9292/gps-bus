package biz.ugur.busroutebackend.transport.infrastructure.messaging;

import biz.ugur.busroutebackend.interfaces.websocket.VehiclePositionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class VehiclePositionWebSocketPublisher {

    private final VehiclePositionHandler vehiclePositionHandler;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public VehiclePositionWebSocketPublisher(VehiclePositionHandler vehiclePositionHandler,
                                             ReactiveRedisTemplate<String, Object> redisTemplate,
                                             ObjectMapper objectMapper) {
        this.vehiclePositionHandler = vehiclePositionHandler;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }
    public Mono<Void> broadcastVehiclePosition(VehiclePositionWebSocketMessage message) {
        return Mono.fromRunnable(() -> {
            vehiclePositionHandler.broadcastVehiclePosition(message);

            // Публикуем в Redis для других инстансов приложения (если кластер)
            try {
                String messageJson = objectMapper.writeValueAsString(message);
                redisTemplate.convertAndSend("vehicle-position-updates", messageJson)
                        .subscribe();
            } catch (Exception e) {
                log.warn("Failed to publish to Redis: {}", e.getMessage());
            }
        });
    }

    public Mono<Void> broadcastRouteAssignment(VehicleRouteAssignmentMessage message) {
        log.debug("Broadcasting route assignment: {}", message.getVehicleId());

        // TODO: Отправка через WebSocket
        // messagingTemplate.convertAndSend("/topic/route-assignments", message);

        return Mono.empty();
    }
}
