package biz.ugur.busroutebackend.transport.scheduler;

import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsProviderType;
import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionUpdateResult;
import biz.ugur.busroutebackend.transport.application.service.GpsDataAggregatorService;
import biz.ugur.busroutebackend.transport.application.usecase.UpdateVehiclePositionsUseCase;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.scheduler.dto.GpsUpdateStats;
import biz.ugur.busroutebackend.transport.scheduler.dto.HealthStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;


@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VehicleDataScheduler {

    private static final String GPS_UPDATE_STATS_KEY = "gps:update:stats";
    private static final String GPS_HEALTH_KEY = "gps:health";
    private static final Duration STUCK_THRESHOLD = Duration.ofSeconds(150);

    private final GpsDataAggregatorService gpsDataAggregator;
    private final UpdateVehiclePositionsUseCase updateVehiclePositionsUseCase;
    private final VehicleRepository vehicleRepository;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final SchedulerProperties schedulerProperties;

    private final AtomicBoolean gpsUpdateInProgress = new AtomicBoolean(false);
    private volatile Instant lastGpsUpdateStartTime = null;

    public VehicleDataScheduler(GpsDataAggregatorService gpsDataAggregator,
                                UpdateVehiclePositionsUseCase updateVehiclePositionsUseCase,
                                VehicleRepository vehicleRepository,
                                ReactiveRedisTemplate<String, Object> redisTemplate,
                                SchedulerProperties schedulerProperties) {
        this.gpsDataAggregator = gpsDataAggregator;
        this.updateVehiclePositionsUseCase = updateVehiclePositionsUseCase;
        this.vehicleRepository = vehicleRepository;
        this.redisTemplate = redisTemplate;
        this.schedulerProperties = schedulerProperties;

        log.info("VehicleDataScheduler initialized with {} GPS providers",
                gpsDataAggregator.getEnabledProviderCount());
    }

    @Scheduled(cron = "0/10 * * * * *")
    public void updateVehiclePositions() {
        log.debug("GPS scheduler triggered. inProgress={}, lastStartTime={}, providers={}",
                gpsUpdateInProgress.get(), lastGpsUpdateStartTime,
                gpsDataAggregator.getEnabledProviderCount());

        resetStuckFlagIfNeeded();

        if (!gpsUpdateInProgress.compareAndSet(false, true)) {
            log.warn("GPS update already in progress, skipping this cycle");
            return;
        }

        Instant startTime = Instant.now();
        lastGpsUpdateStartTime = startTime;

        executeGpsUpdate(startTime)
                .doFinally(signal -> {
                    gpsUpdateInProgress.set(false);
                    lastGpsUpdateStartTime = null;
                    log.debug("GPS update finished with signal: {}", signal);
                })
                .subscribe();
    }

    private Mono<Void> executeGpsUpdate(Instant startTime) {
        int batchSize = schedulerProperties.getGps().getBatchSize();
        int parallelWorkers = schedulerProperties.getGps().getParallelWorkers();
        Duration batchTimeout = schedulerProperties.getGps().getBatchTimeout();
        Duration totalTimeout = schedulerProperties.getGps().getTotalTimeout();

        return vehicleRepository.findAllDeviceIdsGroupedByProvider()
                .flatMap(this::fetchPositionsIfNotEmpty)
                .doOnNext(positions -> log.debug("Fetched {} GPS positions, processing in batches of {}",
                        positions.size(), batchSize))
                .flatMapIterable(positions -> positions)
                .buffer(batchSize)
                .parallel(parallelWorkers)
                .runOn(Schedulers.parallel())
                .flatMap(batch -> processBatch(batch, batchTimeout))
                .sequential()
                .reduce(VehiclePositionUpdateResult.empty(), VehiclePositionUpdateResult::merge)
                .timeout(totalTimeout)
                .flatMap(result -> handleSuccess(result, startTime))
                .onErrorResume(error -> handleError(error, startTime))
                .then();
    }

    private Mono<List<GpsPositionDTO>> fetchPositionsIfNotEmpty(Map<GpsProviderType, List<String>> devicesByProvider) {
        if (devicesByProvider.isEmpty()) {
            log.warn("No device IDs found in database, skipping GPS update");
            return Mono.just(List.of());
        }

        int totalDevices = devicesByProvider.values().stream().mapToInt(List::size).sum();
        log.info("Found {} device IDs across {} providers, fetching GPS positions",
                totalDevices, devicesByProvider.size());

        return gpsDataAggregator.fetchPositionsGroupedByProvider(devicesByProvider);
    }

    private Mono<VehiclePositionUpdateResult> processBatch(List<GpsPositionDTO> batch, Duration timeout) {
        return updateVehiclePositionsUseCase.execute(batch)
                .timeout(timeout)
                .onErrorResume(error -> {
                    log.error("Failed to process GPS batch of {} vehicles: {} - {}",
                            batch.size(), error.getClass().getSimpleName(), error.getMessage());
                    return Mono.just(VehiclePositionUpdateResult.failed(batch.size()));
                });
    }

    private Mono<VehiclePositionUpdateResult> handleSuccess(VehiclePositionUpdateResult result, Instant startTime) {
        Duration duration = Duration.between(startTime, Instant.now());

        log.info("GPS update completed: duration={}ms, updated={}, created={}, failed={}, invalid={}, conflict={}",
                duration.toMillis(),
                result.updatedCount(),
                result.createdCount(),
                result.failedCount(),
                result.invalidCount(),
                result.conflictCount());

        return saveGpsUpdateStats(result, duration)
                .thenReturn(result);
    }

    private Mono<VehiclePositionUpdateResult> handleError(Throwable error, Instant startTime) {
        Duration duration = Duration.between(startTime, Instant.now());
        log.error("GPS update failed after {}ms: {}", duration.toMillis(), error.getMessage());

        return saveGpsUpdateError(error, duration)
                .thenReturn(VehiclePositionUpdateResult.empty());
    }

    private void resetStuckFlagIfNeeded() {
        if (gpsUpdateInProgress.get() && lastGpsUpdateStartTime != null) {
            Duration stuckDuration = Duration.between(lastGpsUpdateStartTime, Instant.now());
            if (stuckDuration.compareTo(STUCK_THRESHOLD) >= 0) {
                log.warn("GPS update flag stuck for {}s, forcing reset", stuckDuration.toSeconds());
                gpsUpdateInProgress.set(false);
                lastGpsUpdateStartTime = null;
            }
        }
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void checkExternalApisHealth() {
        log.debug("Starting health check for {} GPS providers", gpsDataAggregator.getEnabledProviderCount());

        gpsDataAggregator.healthCheckAll()
                .flatMap(results -> {
                    results.forEach((provider, healthy) ->
                            log.info("GPS provider {} health: {}", provider, healthy ? "OK" : "FAILED"));

                    boolean anyHealthy = results.values().stream().anyMatch(Boolean::booleanValue);
                    return saveHealthStatus(GPS_HEALTH_KEY, anyHealthy)
                            .thenReturn(results);
                })
                .doOnSuccess(results -> log.debug("Health check completed for {} providers", results.size()))
                .doOnError(error -> log.error("Health check failed: {}", error.getMessage()))
                .onErrorResume(error -> Mono.empty())
                .subscribe();
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldData() {
        log.info("Starting cleanup of old cached data");

        String pattern = "gps:update:stats:*";
        redisTemplate.keys(pattern)
                .filter(key -> isOlderThanDays(key, 7))
                .flatMap(redisTemplate::delete)
                .collectList()
                .doOnSuccess(deleted -> log.info("Cleaned up {} old GPS stats entries", deleted.size()))
                .doOnError(error -> log.warn("Failed to cleanup old GPS stats: {}", error.getMessage()))
                .onErrorResume(error -> Mono.just(List.of()))
                .subscribe();
    }

    private Mono<Void> saveGpsUpdateStats(VehiclePositionUpdateResult result, Duration duration) {
        GpsUpdateStats stats = new GpsUpdateStats(
                result.updatedCount(),
                result.createdCount(),
                result.failedCount(),
                result.invalidCount(),
                result.conflictCount(),
                duration.toMillis(),
                Instant.now(),
                true
        );

        return saveStats(stats);
    }

    private Mono<Void> saveGpsUpdateError(Throwable error, Duration duration) {
        GpsUpdateStats stats = new GpsUpdateStats(
                0, 0, 0, 0, 0,
                duration.toMillis(),
                Instant.now(),
                false,
                error.getMessage()
        );

        return saveStats(stats);
    }

    private Mono<Void> saveStats(GpsUpdateStats stats) {
        String key = GPS_UPDATE_STATS_KEY + ":" + Instant.now().getEpochSecond();

        return redisTemplate.opsForValue()
                .set(key, stats, Duration.ofDays(7))
                .then()
                .onErrorResume(error -> {
                    log.warn("Failed to save GPS stats to Redis: {}", error.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> saveHealthStatus(String key, boolean healthy) {
        HealthStatus status = new HealthStatus(healthy, Instant.now());

        return redisTemplate.opsForValue()
                .set(key, status, Duration.ofMinutes(10))
                .then()
                .onErrorResume(error -> {
                    log.warn("Failed to save health status to Redis: {}", error.getMessage());
                    return Mono.empty();
                });
    }

    private boolean isOlderThanDays(String key, int days) {
        try {
            String[] parts = key.split(":");
            long timestamp = Long.parseLong(parts[parts.length - 1]);
            long cutoff = Instant.now().minus(Duration.ofDays(days)).getEpochSecond();
            return timestamp < cutoff;
        } catch (Exception e) {
            return false;
        }
    }
}
