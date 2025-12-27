package biz.ugur.busroutebackend.transport.infrastructure.messaging;

import biz.ugur.busroutebackend.transport.domain.event.VehicleAssignedToRouteEvent;
import biz.ugur.busroutebackend.transport.domain.event.VehiclePositionUpdatedEvent;
import biz.ugur.busroutebackend.transport.domain.event.VehicleRegisteredEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


@Component
@Slf4j
public class VehicleEventHandler {

    private static final Duration POSITION_CACHE_TTL = Duration.ofMinutes(10);
    private static final Duration ROUTE_CACHE_TTL = Duration.ofHours(24);
    private static final Duration UNASSIGN_ROUTE_TTL = Duration.ofMinutes(5);
    private static final Duration VEHICLE_INFO_TTL = Duration.ofDays(30);

    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_RETRY_ATTEMPTS = 2;
    private static final Duration RETRY_DELAY = Duration.ofMillis(100);

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final VehiclePositionWebSocketPublisher webSocketPublisher;

    public VehicleEventHandler(ReactiveRedisTemplate<String, Object> redisTemplate,
                               VehiclePositionWebSocketPublisher webSocketPublisher) {
        this.redisTemplate = redisTemplate;
        this.webSocketPublisher = webSocketPublisher;
    }

    @Async
    @EventListener
    public CompletableFuture<Void> handleVehiclePositionUpdated(VehiclePositionUpdatedEvent event) {
        log.debug("Processing vehicle position update: vehicleId={}, plate={}",
                event.getVehicleId(), event.getLicensePlate());

        return cacheVehiclePosition(event)
                .then(broadcastPositionUpdate(event))
                .timeout(OPERATION_TIMEOUT)
                .retryWhen(createRetrySpec("position update"))
                .doOnSuccess(v -> log.trace("Vehicle position processed: {}", event.getVehicleId()))
                .doOnError(error -> log.error("Failed to process vehicle position: vehicleId={}, error={}",
                        event.getVehicleId(), error.getMessage()))
                .onErrorComplete()
                .toFuture();
    }

    @Async
    @EventListener
    public CompletableFuture<Void> handleVehicleAssignedToRoute(VehicleAssignedToRouteEvent event) {
        log.debug("Processing route assignment: vehicleId={}, newRouteId={}",
                event.getVehicleId(), event.getNewRouteId());

        return updateRouteAssignmentCache(event)
                .then(notifyRouteAssignmentChange(event))
                .timeout(OPERATION_TIMEOUT)
                .retryWhen(createRetrySpec("route assignment"))
                .doOnSuccess(v -> log.trace("Route assignment processed: {}", event.getVehicleId()))
                .doOnError(error -> log.error("Failed to process route assignment: vehicleId={}, error={}",
                        event.getVehicleId(), error.getMessage()))
                .onErrorComplete()
                .toFuture();
    }

    @Async
    @EventListener
    public CompletableFuture<Void> handleVehicleRegistered(VehicleRegisteredEvent event) {
        log.info("New vehicle registered: deviceId={}, plate={}",
                event.getDeviceId(), event.getLicensePlate());

        return initializeVehicleCache(event)
                .then()
                .timeout(OPERATION_TIMEOUT)
                .retryWhen(createRetrySpec("vehicle registration"))
                .doOnSuccess(v -> log.debug("Vehicle cache initialized: {}", event.getVehicleId()))
                .doOnError(error -> log.error("Failed to initialize vehicle cache: vehicleId={}, error={}",
                        event.getVehicleId(), error.getMessage()))
                .onErrorComplete()
                .toFuture();
    }

    private Mono<Boolean> cacheVehiclePosition(VehiclePositionUpdatedEvent event) {
        String key = "vehicle:position:" + event.getVehicleId();

        Map<String, Object> data = Map.of(
                "vehicleId", event.getVehicleId(),
                "deviceId", nullSafe(event.getDeviceId()),
                "licensePlate", nullSafe(event.getLicensePlate()),
                "latitude", event.getLatitude(),
                "longitude", event.getLongitude(),
                "speedKmh", nullSafe(event.getSpeedKmh(), 0.0),
                "inMotion", nullSafe(event.getIsInMotion(), false),
                "timestamp", String.valueOf(event.getPositionTimestamp()),
                "lastUpdated", String.valueOf(event.getOccurredAt())
        );

        return redisTemplate.opsForValue()
                .set(key, data, POSITION_CACHE_TTL)
                .doOnSuccess(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        log.trace("Cached vehicle position: {}", event.getVehicleId());
                    }
                });
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
                event.getPositionTimestamp(),
                event.getCourse(),
                event.getLine()
        );

        return webSocketPublisher.broadcastVehiclePosition(msg)
                .doOnSuccess(v -> log.trace("Broadcasted position: {}", event.getVehicleId()));
    }

    private Mono<Boolean> updateRouteAssignmentCache(VehicleAssignedToRouteEvent event) {
        String key = "vehicle:route:" + event.getVehicleId();

        Map<String, Object> data = Map.of(
                "vehicleId", event.getVehicleId(),
                "licensePlate", nullSafe(event.getLicensePlate()),
                "previousRouteId", nullSafe(event.getPreviousRouteId()),
                "newRouteId", nullSafe(event.getNewRouteId()),
                "assignmentTime", String.valueOf(event.getOccurredAt())
        );

        Duration ttl = event.isUnassignment() ? UNASSIGN_ROUTE_TTL : ROUTE_CACHE_TTL;

        return redisTemplate.opsForValue()
                .set(key, data, ttl)
                .doOnSuccess(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        log.trace("Updated route cache: {}", event.getVehicleId());
                    }
                });
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
                .doOnSuccess(v -> log.trace("Broadcasted route assignment: {}", event.getVehicleId()));
    }

    private Mono<Boolean> initializeVehicleCache(VehicleRegisteredEvent event) {
        String key = "vehicle:info:" + event.getVehicleId();

        Map<String, Object> info = Map.of(
                "vehicleId", event.getVehicleId(),
                "deviceId", nullSafe(event.getDeviceId()),
                "licensePlate", nullSafe(event.getLicensePlate()),
                "registeredAt", String.valueOf(event.getOccurredAt()),
                "status", "active"
        );

        return redisTemplate.opsForValue()
                .set(key, info, VEHICLE_INFO_TTL)
                .doOnSuccess(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        log.trace("Vehicle info cached: {}", event.getVehicleId());
                    }
                });
    }

    private Retry createRetrySpec(String operationName) {
        return Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_DELAY)
                .filter(this::isRetryableException)
                .doBeforeRetry(signal -> log.warn("Retrying {} after error: {}, attempt {}/{}",
                        operationName,
                        signal.failure().getMessage(),
                        signal.totalRetries() + 1,
                        MAX_RETRY_ATTEMPTS));
    }

    private boolean isRetryableException(Throwable ex) {
        return ex instanceof RedisConnectionFailureException
                || ex.getCause() instanceof RedisConnectionFailureException;
    }

    private static String nullSafe(String value) {
        return value != null ? value : "";
    }

    private static <T> T nullSafe(T value, T defaultValue) {
        return value != null ? value : defaultValue;
    }
}
