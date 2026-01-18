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
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;


@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VehicleDataScheduler {

    private static final String GPS_UPDATE_LOCK_NAME = "gps-scheduler";
    private static final Duration UPDATE_INTERVAL = Duration.ofSeconds(10);
    private static final ZoneId TIMEZONE = ZoneId.of("Asia/Ashgabat");
    private static final int ACTIVE_HOUR_START = 6;
    private static final int ACTIVE_HOUR_END = 23;

    private final GpsDataAggregatorService gpsDataAggregator;
    private final UpdateVehiclePositionsUseCase updateVehiclePositionsUseCase;
    private final VehicleRepository vehicleRepository;
    private final GpsUpdateStatisticsService statisticsService;
    private final SchedulerProperties schedulerProperties;
    private final RedisDistributedLock distributedLock;
    private final DistributedLockProperties lockProperties;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Disposable schedulerDisposable;

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

    @PostConstruct
    public void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting GPS update scheduler loop");
            scheduleNextUpdate();
        }
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        if (schedulerDisposable != null && !schedulerDisposable.isDisposed()) {
            schedulerDisposable.dispose();
            log.info("GPS update scheduler stopped");
        }
    }

    private void scheduleNextUpdate() {
        if (!running.get()) {
            return;
        }

        schedulerDisposable = Mono.delay(UPDATE_INTERVAL)
                .flatMap(tick -> {
                    if (!isActiveHours()) {
                        log.debug("Outside active hours ({}-{}), skipping GPS update",
                                ACTIVE_HOUR_START, ACTIVE_HOUR_END);
                        return Mono.empty();
                    }
                    return executeWithLock();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .doFinally(signal -> scheduleNextUpdate())
                .subscribe();
    }

    private boolean isActiveHours() {
        int hour = LocalTime.now(TIMEZONE).getHour();
        return hour >= ACTIVE_HOUR_START && hour < ACTIVE_HOUR_END;
    }

    private Mono<Void> executeWithLock() {
        Duration lockTimeout = lockProperties.getGpsSchedulerLockTimeout();

        return Mono.usingWhen(
                distributedLock.tryAcquire(GPS_UPDATE_LOCK_NAME, lockTimeout)
                        .timeout(Duration.ofSeconds(5)),

                lockHandle -> {
                    Instant startTime = Instant.now();
                    return executeGpsUpdate(startTime)
                            .timeout(lockTimeout.minusSeconds(10));
                },

                this::releaseLock,
                (lockHandle, error) -> releaseLock(lockHandle),
                this::releaseLock
        ).onErrorResume(error -> {
            if (!(error instanceof java.util.concurrent.TimeoutException)) {
                log.error("GPS update error: {}", error.getMessage());
            }
            return Mono.empty();
        });
    }

    private Mono<Void> releaseLock(RedisDistributedLock.LockHandle lockHandle) {
        return distributedLock.release(lockHandle)
                .timeout(Duration.ofSeconds(5))
                .onErrorResume(e -> Mono.just(false))
                .then();
    }

    private Mono<Void> executeGpsUpdate(Instant startTime) {
        int batchSize = schedulerProperties.getGps().getBatchSize();
        int parallelWorkers = schedulerProperties.getGps().getParallelWorkers();
        Duration batchTimeout = schedulerProperties.getGps().getBatchTimeout();
        Duration totalTimeout = schedulerProperties.getGps().getTotalTimeout();

        return vehicleRepository.findAllDeviceIdsGroupedByProvider()
                .flatMap(this::fetchPositionsIfNotEmpty)
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

        return gpsDataAggregator.fetchPositionsGroupedByProvider(devicesByProvider)
                .doOnNext(positions -> {
                    int missing = totalDevices - positions.size();
                    if (missing > 0) {
                        log.warn("GPS API returned {} of {} positions. Missing {} devices",
                                positions.size(), totalDevices, missing);
                    }
                });
    }

    private Mono<VehiclePositionUpdateResult> processBatch(List<GpsPositionDTO> batch, Duration timeout) {
        return updateVehiclePositionsUseCase.execute(batch)
                .timeout(timeout)
                .onErrorResume(error -> {
                    log.error("Failed to process GPS batch of {} vehicles: {}",
                            batch.size(), error.getMessage());
                    return Mono.just(VehiclePositionUpdateResult.failed(batch.size()));
                });
    }

    private Mono<VehiclePositionUpdateResult> handleSuccess(VehiclePositionUpdateResult result, Instant startTime) {
        Duration duration = Duration.between(startTime, Instant.now());

        log.debug("GPS update: {}ms, updated={}, failed={}",
                duration.toMillis(), result.updatedCount(), result.failedCount());

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
        gpsDataAggregator.healthCheckAll()
                .flatMap(results -> {
                    results.forEach((provider, healthy) ->
                            log.info("GPS provider {} health: {}", provider, healthy ? "OK" : "FAILED"));

                    boolean anyHealthy = results.values().stream().anyMatch(Boolean::booleanValue);
                    return statisticsService.saveHealthStatus(anyHealthy)
                            .thenReturn(results);
                })
                .onErrorResume(error -> {
                    log.error("Health check failed: {}", error.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldData() {
        log.info("Starting cleanup of old cached data");
        statisticsService.cleanupOldStats().subscribe();
    }
}
