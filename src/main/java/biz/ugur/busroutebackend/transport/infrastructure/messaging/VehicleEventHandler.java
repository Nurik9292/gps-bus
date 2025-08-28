package biz.ugur.busroutebackend.transport.infrastructure.messaging;

import biz.ugur.busroutebackend.transport.domain.event.VehicleAssignedToRouteEvent;
import biz.ugur.busroutebackend.transport.domain.event.VehiclePositionUpdatedEvent;
import biz.ugur.busroutebackend.transport.domain.event.VehicleRegisteredEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class VehicleEventHandler {

    private static final Duration POSITION_CACHE_TTL = Duration.ofMinutes(10);
    private static final Duration ROUTE_CACHE_TTL = Duration.ofHours(24);
    private static final Duration UNASSIGN_ROUTE_TTL = Duration.ofMinutes(5);
    private static final Duration VEHICLE_INFO_TTL = Duration.ofDays(30);

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final VehiclePositionWebSocketPublisher webSocketPublisher;

    public VehicleEventHandler(ReactiveRedisTemplate<String, Object> redisTemplate,
                               VehiclePositionWebSocketPublisher webSocketPublisher) {
        this.redisTemplate = redisTemplate;
        this.webSocketPublisher = webSocketPublisher;
    }

    @EventListener
    public void handleVehiclePositionUpdated(VehiclePositionUpdatedEvent event) {
        if (event == null) {
            log.warn("Received null VehiclePositionUpdatedEvent");
            return;
        }
        log.info("Handling VehiclePositionUpdated: {}", event);

        cacheVehiclePosition(event)
                .then(updateVehicleStatistics(event))
                .then(broadcastPositionUpdate(event))
                .publishOn(Schedulers.boundedElastic())
                .subscribe(
                        unused -> log.trace("Vehicle position processed: {}", event.getVehicleId()),
                        error -> log.error("Error processing vehicle position: {}", event.getVehicleId(), error)
                );
    }

    @EventListener
    public void handleVehicleAssignedToRoute(VehicleAssignedToRouteEvent event) {
        if (event == null) {
            log.warn("Received null VehicleAssignedToRouteEvent");
            return;
        }
        log.debug("Handling VehicleAssignedToRoute: {}", event);

        updateRouteAssignmentCache(event)
                .then(notifyRouteAssignmentChange(event))
                .publishOn(Schedulers.boundedElastic())
                .subscribe(
                        unused -> log.trace("Route assignment processed: {}", event.getVehicleId()),
                        error -> log.error("Error processing route assignment: {}", event.getVehicleId(), error)
                );
    }

    @EventListener(VehicleRegisteredEvent.class)
    public void handleVehicleRegistered(VehicleRegisteredEvent event) {
        if (event == null) {
            log.warn("Received null VehicleRegisteredEvent");
            return;
        }
        log.info("New vehicle registered: {} | Plate: {}", event.getDeviceId(), event.getLicensePlate());

        initializeVehicleCache(event)
                .publishOn(Schedulers.boundedElastic())
                .subscribe(
                        unused -> log.trace("Vehicle cache initialized: {}", event.getVehicleId()),
                        error -> log.error("Failed to initialize vehicle cache: {}", event.getVehicleId(), error)
                );
    }


    private Mono<Void> cacheVehiclePosition(VehiclePositionUpdatedEvent event) {
        String key = "vehicle:position:" + event.getVehicleId();

        Map<String, Object> data = new HashMap<>();
        data.put("vehicleId", event.getVehicleId());
        data.put("deviceId", event.getDeviceId());
        data.put("licensePlate", event.getLicensePlate());
        data.put("latitude", event.getLatitude());
        data.put("longitude", event.getLongitude());
        data.put("speedKmh", event.getSpeedKmh());
        data.put("inMotion", event.getIsInMotion());
        data.put("timestamp", String.valueOf(event.getPositionTimestamp()));
        data.put("lastUpdated", String.valueOf(event.getOccurredAt()));

        return redisTemplate.opsForValue()
                .set(key, data, POSITION_CACHE_TTL)
                .then(Mono.fromRunnable(() -> log.trace("Cached vehicle position: {}", event.getVehicleId())));
    }

    private Mono<Void> updateVehicleStatistics(VehiclePositionUpdatedEvent event) {
        String statsKey = "vehicles:stats:motion";
        String motionKey = Boolean.TRUE.equals(event.getIsInMotion()) ? "in_motion" : "stopped";

        return redisTemplate.opsForHash()
                .increment(statsKey, motionKey, 1)
                .then(Mono.fromRunnable(() -> log.trace("Updated motion stats for {}", event.getVehicleId())));
    }

    private Mono<Void> broadcastPositionUpdate(VehiclePositionUpdatedEvent event) {
        VehiclePositionWebSocketMessage msg = new VehiclePositionWebSocketMessage(
                event.getVehicleId(),
                event.getLicensePlate(),
                event.getRouteNumber(),
                event.getLatitude(),
                event.getLongitude(),
                event.getSpeedKmh(),
                event.getIsInMotion(),
                event.getPositionTimestamp()
        );

        return webSocketPublisher.broadcastVehiclePosition(msg)
                .then(Mono.fromRunnable(() -> log.trace("Broadcasted position for {}", event.getVehicleId())));
    }

    private Mono<Void> updateRouteAssignmentCache(VehicleAssignedToRouteEvent event) {
        String key = "vehicle:route:" + event.getVehicleId();

        Map<String, Object> data = new HashMap<>();
        data.put("vehicleId", event.getVehicleId());
        data.put("licensePlate", event.getLicensePlate());
        data.put("previousRouteId", event.getPreviousRouteId());
        data.put("newRouteId", event.getNewRouteId());
        data.put("assignmentTime", String.valueOf(event.getOccurredAt()));

        Duration ttl = event.isUnassignment() ? UNASSIGN_ROUTE_TTL : ROUTE_CACHE_TTL;

        return redisTemplate.opsForValue()
                .set(key, data, ttl)
                .then(Mono.fromRunnable(() -> log.trace("Updated route cache: {}", event.getVehicleId())));
    }

    private Mono<Void> notifyRouteAssignmentChange(VehicleAssignedToRouteEvent event) {
        VehicleRouteAssignmentMessage msg = new VehicleRouteAssignmentMessage(
                event.getVehicleId(),
                event.getLicensePlate(),
                event.getPreviousRouteId(),
                event.getNewRouteId(),
                event.getOccurredAt()
        );

        return webSocketPublisher.broadcastRouteAssignment(msg)
                .then(Mono.fromRunnable(() -> log.trace("Broadcasted route assignment: {}", event.getVehicleId())));
    }

    private Mono<Void> initializeVehicleCache(VehicleRegisteredEvent event) {
        String key = "vehicle:info:" + event.getVehicleId();

        Map<String, Object> info = new HashMap<>();
        info.put("vehicleId", event.getVehicleId());
        info.put("deviceId", event.getDeviceId());
        info.put("licensePlate", event.getLicensePlate());
        info.put("registeredAt", String.valueOf(event.getOccurredAt()));
        info.put("status", "active");

        return redisTemplate.opsForValue()
                .set(key, info, VEHICLE_INFO_TTL)
                .then(Mono.fromRunnable(() -> log.trace("Vehicle info cached: {}", event.getVehicleId())));
    }
}
