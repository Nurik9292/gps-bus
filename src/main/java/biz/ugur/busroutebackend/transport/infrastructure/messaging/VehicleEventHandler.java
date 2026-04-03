package biz.ugur.busroutebackend.transport.infrastructure.messaging;

import biz.ugur.busroutebackend.shared.infrastructure.cache.RedisKeyRegistry;
import biz.ugur.busroutebackend.shared.infrastructure.messaging.ReactiveEventBus;
import biz.ugur.busroutebackend.transport.application.services.VehicleEtaEnricherService;
import biz.ugur.busroutebackend.transport.domain.event.VehicleAssignedToRouteEvent;
import biz.ugur.busroutebackend.transport.domain.event.VehiclePositionUpdatedEvent;
import biz.ugur.busroutebackend.transport.domain.event.VehicleRegisteredEvent;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.PredictionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Semaphore;


@Component
@Slf4j
public class VehicleEventHandler {

    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_RETRY_ATTEMPTS = 2;
    private static final Duration RETRY_DELAY = Duration.ofMillis(100);
    private static final Duration ETA_ENRICHMENT_TIMEOUT = Duration.ofMillis(500);

    private static final int POSITION_CONCURRENCY = 16;
    private static final int MAX_ETA_CONCURRENCY = 3;
    private final Semaphore etaSemaphore = new Semaphore(MAX_ETA_CONCURRENCY);
    private static final int ROUTE_ASSIGNMENT_CONCURRENCY = 16;
    private static final int REGISTRATION_CONCURRENCY = 8;

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final VehiclePositionWebSocketPublisher webSocketPublisher;
    private final ReactiveEventBus reactiveEventBus;
    private final VehicleEtaEnricherService vehicleEtaEnricherService;
    private final PredictionProperties predictionProperties;

    private Disposable positionSubscription;
    private Disposable routeAssignmentSubscription;
    private Disposable registrationSubscription;

    public VehicleEventHandler(ReactiveRedisTemplate<String, Object> redisTemplate,
                               VehiclePositionWebSocketPublisher webSocketPublisher,
                               ReactiveEventBus reactiveEventBus,
                               VehicleEtaEnricherService vehicleEtaEnricherService,
                               PredictionProperties predictionProperties) {
        this.redisTemplate = redisTemplate;
        this.webSocketPublisher = webSocketPublisher;
        this.reactiveEventBus = reactiveEventBus;
        this.vehicleEtaEnricherService = vehicleEtaEnricherService;
        this.predictionProperties = predictionProperties;
    }

    @PostConstruct
    public void init() {
        subscribeToPositionUpdates();
        subscribeToRouteAssignments();
        subscribeToRegistrations();
        log.info("VehicleEventHandler initialized with reactive subscriptions");
    }

    @PreDestroy
    public void destroy() {
        disposeIfActive(positionSubscription);
        disposeIfActive(routeAssignmentSubscription);
        disposeIfActive(registrationSubscription);
        log.info("VehicleEventHandler subscriptions disposed");
    }

    private void disposeIfActive(Disposable disposable) {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }


    private void subscribeToPositionUpdates() {
        positionSubscription = reactiveEventBus.on(VehiclePositionUpdatedEvent.class)
                .flatMap(this::handleVehiclePositionUpdated, POSITION_CONCURRENCY)
                .subscribe(
                        v -> {},
                        error -> log.error("Position subscription error", error),
                        () -> log.info("Position subscription completed")
                );
    }

    private void subscribeToRouteAssignments() {
        routeAssignmentSubscription = reactiveEventBus.on(VehicleAssignedToRouteEvent.class)
                .flatMap(this::handleVehicleAssignedToRoute, ROUTE_ASSIGNMENT_CONCURRENCY)
                .subscribe(
                        v -> {},
                        error -> log.error("Route assignment subscription error", error),
                        () -> log.info("Route assignment subscription completed")
                );
    }

    private void subscribeToRegistrations() {
        registrationSubscription = reactiveEventBus.on(VehicleRegisteredEvent.class)
                .flatMap(this::handleVehicleRegistered, REGISTRATION_CONCURRENCY)
                .subscribe(
                        v -> {},
                        error -> log.error("Registration subscription error", error),
                        () -> log.info("Registration subscription completed")
                );
    }

    private Mono<Void> handleVehiclePositionUpdated(VehiclePositionUpdatedEvent event) {
        log.debug("Processing vehicle position update: vehicleId={}, plate={}",
                event.getVehicleId(), event.getLicensePlate());

        // When prediction is enabled, the prediction service (PositionPredictionScheduler)
        // handles all WebSocket broadcasting every second for MOVING vehicles.
        // Stationary vehicles (isInMotion=false) are excluded from the prediction cycle,
        // so we still broadcast their GPS directly to keep them visible on the map.
        boolean handledByPrediction = predictionProperties.isEnabled()
                && Boolean.TRUE.equals(event.getIsInMotion());
        Mono<Void> broadcastMono = handledByPrediction
                ? Mono.empty()
                : broadcastPositionUpdate(event);

        return cacheVehiclePosition(event)
                .then(broadcastMono)
                .timeout(OPERATION_TIMEOUT)
                .retryWhen(createRetrySpec("position update"))
                .doOnSuccess(v -> log.trace("Vehicle position processed: {}", event.getVehicleId()))
                .doOnError(error -> log.error("Failed to process vehicle position: vehicleId={}, error={}",
                        event.getVehicleId(), error.getMessage()))
                .onErrorComplete();
    }

    private Mono<Void> handleVehicleAssignedToRoute(VehicleAssignedToRouteEvent event) {
        log.debug("Processing route assignment: vehicleId={}, newRouteId={}",
                event.getVehicleId(), event.getNewRouteId());

        return updateRouteAssignmentCache(event)
                .then(notifyRouteAssignmentChange(event))
                .timeout(OPERATION_TIMEOUT)
                .retryWhen(createRetrySpec("route assignment"))
                .doOnSuccess(v -> log.trace("Route assignment processed: {}", event.getVehicleId()))
                .doOnError(error -> log.error("Failed to process route assignment: vehicleId={}, error={}",
                        event.getVehicleId(), error.getMessage()))
                .onErrorComplete();
    }

    private Mono<Void> handleVehicleRegistered(VehicleRegisteredEvent event) {
        log.info("New vehicle registered: deviceId={}, plate={}",
                event.getDeviceId(), event.getLicensePlate());

        return initializeVehicleCache(event)
                .then()
                .timeout(OPERATION_TIMEOUT)
                .retryWhen(createRetrySpec("vehicle registration"))
                .doOnSuccess(v -> log.debug("Vehicle cache initialized: {}", event.getVehicleId()))
                .doOnError(error -> log.error("Failed to initialize vehicle cache: vehicleId={}, error={}",
                        event.getVehicleId(), error.getMessage()))
                .onErrorComplete();
    }

    private Mono<Boolean> cacheVehiclePosition(VehiclePositionUpdatedEvent event) {
        String key = RedisKeyRegistry.Vehicle.position(event.getVehicleId());

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
                .set(key, data, RedisKeyRegistry.Vehicle.POSITION_TTL)
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

        if (event.getRouteNumber() == null) {
            return webSocketPublisher.broadcastVehiclePosition(msg)
                    .doOnSuccess(v -> log.trace("Broadcasted position: {}", event.getVehicleId()));
        }

        // ETA enrichment is limited to MAX_ETA_CONCURRENCY concurrent DB queries.
        // If all slots are busy the message is broadcast without ETA to keep the
        // connection pool free for route search and other requests.
        if (etaSemaphore.tryAcquire()) {
            return vehicleEtaEnricherService.enrichWithEta(msg)
                    .timeout(ETA_ENRICHMENT_TIMEOUT)
                    .onErrorReturn(msg)
                    .doFinally(signal -> etaSemaphore.release())
                    .flatMap(webSocketPublisher::broadcastVehiclePosition)
                    .doOnSuccess(v -> log.trace("Broadcasted position (ETA): {}", event.getVehicleId()));
        } else {
            return webSocketPublisher.broadcastVehiclePosition(msg)
                    .doOnSuccess(v -> log.trace("Broadcasted position (no ETA slot): {}", event.getVehicleId()));
        }
    }

    private Mono<Boolean> updateRouteAssignmentCache(VehicleAssignedToRouteEvent event) {
        String key = RedisKeyRegistry.Vehicle.route(event.getVehicleId());

        Map<String, Object> data = Map.of(
                "vehicleId", event.getVehicleId(),
                "licensePlate", nullSafe(event.getLicensePlate()),
                "previousRouteId", nullSafe(event.getPreviousRouteId()),
                "newRouteId", nullSafe(event.getNewRouteId()),
                "assignmentTime", String.valueOf(event.getOccurredAt())
        );

        Duration ttl = event.isUnassignment()
                ? RedisKeyRegistry.Vehicle.UNASSIGN_ROUTE_TTL
                : RedisKeyRegistry.Vehicle.ROUTE_TTL;

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
        String key = RedisKeyRegistry.Vehicle.info(event.getVehicleId());

        Map<String, Object> info = Map.of(
                "vehicleId", event.getVehicleId(),
                "deviceId", nullSafe(event.getDeviceId()),
                "licensePlate", nullSafe(event.getLicensePlate()),
                "registeredAt", String.valueOf(event.getOccurredAt()),
                "status", "active"
        );

        return redisTemplate.opsForValue()
                .set(key, info, RedisKeyRegistry.Vehicle.INFO_TTL)
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
