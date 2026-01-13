package biz.ugur.busroutebackend.transport.infrastructure.messaging;

import biz.ugur.busroutebackend.interfaces.websocket.VehiclePositionHandler;
import biz.ugur.busroutebackend.shared.infrastructure.redis.RedisPubSubHealthTracker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


@Component
@Slf4j
public class VehiclePositionWebSocketPublisher {

    private static final String POSITION_UPDATES_CHANNEL = "vehicle-position-updates";
    private static final String ROUTE_ASSIGNMENTS_CHANNEL = "vehicle-route-assignments";

    private final VehiclePositionHandler vehiclePositionHandler;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final RedisPubSubHealthTracker healthTracker;

    public VehiclePositionWebSocketPublisher(VehiclePositionHandler vehiclePositionHandler,
                                             ReactiveRedisTemplate<String, Object> redisTemplate,
                                             RedisPubSubHealthTracker healthTracker) {
        this.vehiclePositionHandler = vehiclePositionHandler;
        this.redisTemplate = redisTemplate;
        this.healthTracker = healthTracker;
    }


    public Mono<Void> broadcastVehiclePosition(VehiclePositionWebSocketMessage message) {
        if (message == null) {
            log.warn("Cannot broadcast null vehicle position message");
            return Mono.empty();
        }

        Mono<Void> localBroadcast = Mono.fromRunnable(() ->
                vehiclePositionHandler.broadcastVehiclePosition(message)
        );

        Mono<Void> redisPublish = publishToRedis(POSITION_UPDATES_CHANNEL, message);

        return localBroadcast.then(redisPublish);
    }

    public Mono<Void> broadcastRouteAssignment(VehicleRouteAssignmentMessage message) {
        if (message == null) {
            log.warn("Cannot broadcast null route assignment message");
            return Mono.empty();
        }

        log.info("Broadcasting route assignment: vehicleId={}, previousRoute={}, newRoute={}",
                message.getVehicleId(), message.getPreviousRouteId(), message.getNewRouteId());

        return publishToRedis(ROUTE_ASSIGNMENTS_CHANNEL, message);
    }


    public boolean isRedisHealthy() {
        return healthTracker.isHealthy();
    }


    public RedisPubSubHealthTracker.RedisPubSubHealthStats getHealthStats() {
        return healthTracker.getStats();
    }

    private Mono<Void> publishToRedis(String channel, Object message) {
        return redisTemplate.convertAndSend(channel, message)
                .doOnSuccess(receivers -> healthTracker.recordSuccess(channel))
                .doOnError(error -> healthTracker.recordFailure(channel, error))
                .onErrorResume(error -> {
                    return Mono.empty();
                })
                .then();
    }
}
