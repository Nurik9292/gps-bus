package biz.ugur.busroutebackend.transport.scheduler;

import biz.ugur.busroutebackend.shared.infrastructure.redis.DistributedLockProperties;
import biz.ugur.busroutebackend.shared.infrastructure.redis.RedisDistributedLock;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsProviderType;
import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionUpdateResult;
import biz.ugur.busroutebackend.transport.application.service.GpsDataAggregatorService;
import biz.ugur.busroutebackend.transport.application.usecase.UpdateVehiclePositionsUseCase;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.scheduler.service.GpsUpdateStatisticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;


@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VehicleDataScheduler {

    private static final String GPS_UPDATE_LOCK_NAME = "gps-scheduler";

    private final GpsDataAggregatorService gpsDataAggregator;
    private final UpdateVehiclePositionsUseCase updateVehiclePositionsUseCase;
    private final VehicleRepository vehicleRepository;
    private final GpsUpdateStatisticsService statisticsService;
    private final SchedulerProperties schedulerProperties;
    private final RedisDistributedLock distributedLock;
    private final DistributedLockProperties lockProperties;

    public VehicleDataScheduler(GpsDataAggregatorService gpsDataAggregator,
                                UpdateVehiclePositionsUseCase updateVehiclePositionsUseCase,
                                VehicleRepository vehicleRepository,
                                GpsUpdateStatisticsService statisticsService,
                                SchedulerProperties schedulerProperties,
                                RedisDistributedLock distributedLock,
                                DistributedLockProperties lockProperties) {
        this.gpsDataAggregator = gpsDataAggregator;
        this.updateVehiclePositionsUseCase = updateVehiclePositionsUseCase;
        this.vehicleRepository = vehicleRepository;
        this.statisticsService = statisticsService;
        this.schedulerProperties = schedulerProperties;
        this.distributedLock = distributedLock;
        this.lockProperties = lockProperties;

        log.info("VehicleDataScheduler initialized with {} GPS providers, distributedLock={}",
                gpsDataAggregator.getEnabledProviderCount(),
                lockProperties.isEnabled() ? "enabled" : "disabled");
    }

    @Scheduled(cron = "0/10 * 6-23 * * *", zone = "Asia/Ashgabat")
    public void updateVehiclePositions() {
        try {
            Duration lockTimeout = lockProperties.getGpsSchedulerLockTimeout();
            Duration lockAcquireTimeout = Duration.ofSeconds(5);

            Mono.usingWhen(
                    distributedLock.tryAcquire(GPS_UPDATE_LOCK_NAME, lockTimeout)
                            .timeout(lockAcquireTimeout)
                            .switchIfEmpty(Mono.empty()),

                    lockHandle -> {
                        Instant startTime = Instant.now();
                        return executeGpsUpdate(startTime)
                                .timeout(lockTimeout.minusSeconds(10));
                    },

                            this::releaseLock,
                    (lockHandle, error) -> releaseLock(lockHandle),
                            this::releaseLock
            )
                    .subscribeOn(Schedulers.boundedElastic())
                    .onErrorResume(this::handleSchedulerError)
                    .subscribe();
        } catch (Exception e) {
            log.error("GPS scheduler failed to start: {}", e.getMessage(), e);
        }
    }

    private Mono<Void> handleSchedulerError(Throwable error) {
        log.error("GPS update failed with unexpected error: {}", error.getMessage(), error);
        return Mono.empty();
    }

    private Mono<Void> releaseLock(RedisDistributedLock.LockHandle lockHandle) {
        return distributedLock.release(lockHandle)
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(error -> Mono.just(false))
                .then();
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
            log.warn("No device IDs found in database (is_active=true, device_id NOT NULL), skipping GPS update");
            return Mono.just(List.of());
        }

        int totalDevices = devicesByProvider.values().stream().mapToInt(List::size).sum();

        log.debug("Fetching GPS positions for {} devices across {} providers",
                totalDevices, devicesByProvider.size());

        return gpsDataAggregator.fetchPositionsGroupedByProvider(devicesByProvider)
                .doOnNext(positions -> {
                    int fetched = positions.size();
                    int missing = totalDevices - fetched;
                    if (missing > 0) {
                        log.warn("API returned {} positions for {} requested devices. Missing {} devices",
                                fetched, totalDevices, missing);
                    } else {
                        log.debug("API returned {} positions for {} requested devices",
                                fetched, totalDevices);
                    }
                });
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

        log.debug("GPS update completed: duration={}ms, updated={}, created={}, failed={}, invalid={}, conflict={}",
                duration.toMillis(),
                result.updatedCount(),
                result.createdCount(),
                result.failedCount(),
                result.invalidCount(),
                result.conflictCount());

        return statisticsService.saveUpdateStats(result, duration)
                .thenReturn(result);
    }

    private Mono<VehiclePositionUpdateResult> handleError(Throwable error, Instant startTime) {
        Duration duration = Duration.between(startTime, Instant.now());
        log.error("GPS update failed after {}ms: {}", duration.toMillis(), error.getMessage());

        return statisticsService.saveUpdateError(error, duration)
                .thenReturn(VehiclePositionUpdateResult.empty());
    }

    @Scheduled(cron = "0 */5 * * * *")
    public void checkExternalApisHealth() {
        log.debug("Starting health check for {} GPS providers", gpsDataAggregator.getEnabledProviderCount());

        gpsDataAggregator.healthCheckAll()
                .flatMap(results -> {
                    results.forEach((provider, healthy) ->
                            log.info("GPS provider {} health: {}", provider, healthy ? "OK" : "FAILED"));

                    boolean anyHealthy = results.values().stream().anyMatch(Boolean::booleanValue);
                    return statisticsService.saveHealthStatus(anyHealthy)
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

        statisticsService.cleanupOldStats()
                .subscribe();
    }
}
