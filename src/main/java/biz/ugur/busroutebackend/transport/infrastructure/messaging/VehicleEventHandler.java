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

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final VehiclePositionWebSocketPublisher webSocketPublisher;

    public VehicleEventHandler(ReactiveRedisTemplate<String, Object> redisTemplate,
                               VehiclePositionWebSocketPublisher webSocketPublisher) {
        this.redisTemplate = redisTemplate;
        this.webSocketPublisher = webSocketPublisher;
    }

    /**
     * Обработка события обновления позиции автобуса
     * - Кэширование позиции в Redis
     * - Отправка через WebSocket клиентам
     * - Обновление статистики
     */
    @EventListener
    public void handleVehiclePositionUpdated(VehiclePositionUpdatedEvent event) {
        log.debug("Handling VehiclePositionUpdated: {}", event);

        // Кэшируем позицию в Redis для быстрого доступа
        cacheVehiclePosition(event)
                .then(updateVehicleStatistics(event))
                .then(broadcastPositionUpdate(event))
                .subscribe(
                        unused -> log.debug("Vehicle position event processed: {}", event.getVehicleId()),
                        error -> log.error("Failed to process vehicle position event: {}", event.getVehicleId(), error)
                );
    }

    /**
     * Обработка события назначения автобуса на маршрут
     * - Обновление кэша назначений
     * - Уведомление о изменении маршрута
     */
    @EventListener
    public void handleVehicleAssignedToRoute(VehicleAssignedToRouteEvent event) {
        log.debug("Handling VehicleAssignedToRoute: {}", event);

        updateRouteAssignmentCache(event)
                .then(Mono.defer(() -> notifyRouteAssignmentChange(event)))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        unused -> log.debug("Vehicle route assignment processed: {}", event.getVehicleId()),
                        error -> log.error("Failed to process route assignment: {}", event.getVehicleId(), error)
                );
    }

    /**
     * Обработка события регистрации нового автобуса
     * - Логирование для аудита
     * - Инициализация кэша
     */
    @EventListener
    public void handleVehicleRegistered(VehicleRegisteredEvent event) {
        log.info("New vehicle registered: {} with plate {}", event.getDeviceId(), event.getLicensePlate());

        initializeVehicleCache(event)
                .subscribe(
                        unused -> log.debug("Vehicle cache initialized: {}", event.getVehicleId()),
                        error -> log.error("Failed to initialize vehicle cache: {}", event.getVehicleId(), error)
                );
    }

    // Приватные методы для обработки событий

    private Mono<Void> cacheVehiclePosition(VehiclePositionUpdatedEvent event) {
        String cacheKey = "vehicle:position:" + event.getVehicleId();

        Map<String, Object> positionData = new HashMap<>();
        positionData.put("vehicleId", event.getVehicleId());
        positionData.put("deviceId", event.getDeviceId());
        positionData.put("licensePlate", event.getLicensePlate());
        positionData.put("latitude", event.getLatitude());
        positionData.put("longitude", event.getLongitude());
        positionData.put("speedKmh", event.getSpeedKmh());
        positionData.put("isInMotion", event.getIsInMotion());
        positionData.put("timestamp", event.getPositionTimestamp().toString());
        positionData.put("lastUpdated", event.getOccurredAt().toString());

        return redisTemplate.opsForValue()
                .set(cacheKey, positionData, Duration.ofMinutes(10))
                .then()
                .doOnSuccess(unused -> log.debug("Cached position for vehicle: {}", event.getVehicleId()));
    }

    private Mono<Void> updateVehicleStatistics(VehiclePositionUpdatedEvent event) {
        // Обновляем счетчики автобусов в движении
        String statsKey = "vehicles:stats:motion";
        String motionKey = event.getIsInMotion() ? "in_motion" : "stopped";

        return redisTemplate.opsForHash()
                .increment(statsKey, motionKey, 1)
                .then()
                .doOnSuccess(unused -> log.trace("Updated vehicle motion statistics"));
    }

    private Mono<Void> broadcastPositionUpdate(VehiclePositionUpdatedEvent event) {
        // Отправляем обновление позиции через WebSocket всем подключенным клиентам
        VehiclePositionWebSocketMessage message = new VehiclePositionWebSocketMessage(
                event.getVehicleId(),
                event.getLicensePlate(),
                event.getLatitude(),
                event.getLongitude(),
                event.getSpeedKmh(),
                event.getIsInMotion(),
                event.getPositionTimestamp()
        );

        return webSocketPublisher.broadcastVehiclePosition(message)
                .doOnSuccess(unused -> log.trace("Broadcasted position update for: {}", event.getVehicleId()));
    }

    private Mono<Void> updateRouteAssignmentCache(VehicleAssignedToRouteEvent event) {
        String cacheKey = "vehicle:route:" + event.getVehicleId();

        Map<String, Object> assignmentData = new HashMap<>();
        assignmentData.put("vehicleId", event.getVehicleId());
        assignmentData.put("licensePlate", event.getLicensePlate());
        assignmentData.put("previousRouteId", event.getPreviousRouteId());
        assignmentData.put("newRouteId", event.getNewRouteId());
        assignmentData.put("assignmentTime", event.getOccurredAt().toString());

        Duration ttl = event.isUnassignment() ? Duration.ofMinutes(5) : Duration.ofHours(24);

        return redisTemplate.opsForValue()
                .set(cacheKey, assignmentData, ttl)
                .then()
                .doOnSuccess(unused -> log.debug("Updated route assignment cache for: {}", event.getVehicleId()));
    }

    private Mono<Void> notifyRouteAssignmentChange(VehicleAssignedToRouteEvent event) {
        // Уведомляем клиентов об изменении назначения автобуса
        VehicleRouteAssignmentMessage message = new VehicleRouteAssignmentMessage(
                event.getVehicleId(),
                event.getLicensePlate(),
                event.getPreviousRouteId(),
                event.getNewRouteId(),
                event.getOccurredAt()
        );

        return webSocketPublisher.broadcastRouteAssignment(message)
                .doOnSuccess(unused -> log.debug("Notified route assignment change: {}", event.getVehicleId()));
    }

    private Mono<Void> initializeVehicleCache(VehicleRegisteredEvent event) {
        String cacheKey = "vehicle:info:" + event.getVehicleId();

        Map<String, Object> vehicleInfo = new HashMap<>();
        vehicleInfo.put("vehicleId", event.getVehicleId());
        vehicleInfo.put("deviceId", event.getDeviceId());
        vehicleInfo.put("licensePlate", event.getLicensePlate());
        vehicleInfo.put("registeredAt", event.getOccurredAt().toString());
        vehicleInfo.put("status", "active");

        return redisTemplate.opsForValue()
                .set(cacheKey, vehicleInfo, Duration.ofDays(30))
                .then()
                .doOnSuccess(unused -> log.debug("Initialized cache for new vehicle: {}", event.getVehicleId()));
    }
}
