package biz.ugur.busroutebackend.transport.scheduler;

import biz.ugur.busroutebackend.shared.infrastructure.external.GpsApiClient;
import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionUpdateResult;
import biz.ugur.busroutebackend.transport.application.services.ExternalApiService;
import biz.ugur.busroutebackend.transport.application.usecase.SyncBusRouteAssignmentsUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.UpdateVehiclePositionsUseCase;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.scheduler.dto.GpsUpdateStats;
import biz.ugur.busroutebackend.transport.scheduler.dto.HealthStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;


@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VehicleDataScheduler {


    private final ExternalApiService externalApiService;
    private final UpdateVehiclePositionsUseCase updateVehiclePositionsUseCase;
    // ЗАКОММЕНТИРОВАНО 2025-12-23: Переход на автономную систему (GPS + Shift Assignments)
    // private final SyncBusRouteAssignmentsUseCase syncBusRouteAssignmentsUseCase;
    private final VehicleRepository vehicleRepository;
    private final GpsApiClient gpsApiClient;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final SchedulerProperties schedulerProperties;

    private final AtomicBoolean gpsUpdateInProgress = new AtomicBoolean(false);
    // ЗАКОММЕНТИРОВАНО 2025-12-23: Bus Info API sync больше не используется
    // private final AtomicBoolean busInfoSyncInProgress = new AtomicBoolean(false);
    private volatile Instant lastGpsUpdateStartTime = null;

    private static final String GPS_UPDATE_STATS_KEY = "gps:update:stats";
    private static final String GPS_HEALTH_KEY = "gps:health";
    private static final String BUS_INFO_HEALTH_KEY = "bus_info:health";

    public VehicleDataScheduler(ExternalApiService externalApiService,
                                UpdateVehiclePositionsUseCase updateVehiclePositionsUseCase,
                                // ЗАКОММЕНТИРОВАНО 2025-12-23: Bus Info API sync
                                // SyncBusRouteAssignmentsUseCase syncBusRouteAssignmentsUseCase,
                                VehicleRepository vehicleRepository,
                                GpsApiClient gpsApiClient,
                                ReactiveRedisTemplate<String, Object> redisTemplate,
                                SchedulerProperties schedulerProperties) {
        this.externalApiService = externalApiService;
        this.updateVehiclePositionsUseCase = updateVehiclePositionsUseCase;
        // ЗАКОММЕНТИРОВАНО 2025-12-23: Bus Info API sync
        // this.syncBusRouteAssignmentsUseCase = syncBusRouteAssignmentsUseCase;
        this.vehicleRepository = vehicleRepository;
        this.gpsApiClient = gpsApiClient;
        this.redisTemplate = redisTemplate;
        this.schedulerProperties = schedulerProperties;
    }


    @Scheduled(cron = "0/10 * * * * *")
    public void updateVehiclePositions() {
        log.info("GPS scheduler triggered. inProgress={}, lastStartTime={}",
                gpsUpdateInProgress.get(), lastGpsUpdateStartTime);

        if (gpsUpdateInProgress.get() && lastGpsUpdateStartTime != null) {
            Duration stuckDuration = Duration.between(lastGpsUpdateStartTime, Instant.now());
            if (stuckDuration.toSeconds() >= 150) {
                log.warn("GPS update flag stuck for {}ms, forcing reset", stuckDuration.toMillis());
                gpsUpdateInProgress.set(false);
                lastGpsUpdateStartTime = null;
            }
        }

        if (!gpsUpdateInProgress.compareAndSet(false, true)) {
            log.warn("GPS update already in progress, skipping this cycle");
            return;
        }

        try {
            Instant startTime = Instant.now();
            lastGpsUpdateStartTime = startTime;

            int batchSize = schedulerProperties.getGps().getBatchSize();
            int parallelWorkers = schedulerProperties.getGps().getParallelWorkers();
            Duration batchTimeout = schedulerProperties.getGps().getBatchTimeout();
            Duration totalTimeout = schedulerProperties.getGps().getTotalTimeout();

            vehicleRepository.findAllDeviceIds()
                    .collectList()
                    .flatMap(deviceIds -> {
                        if (deviceIds.isEmpty()) {
                            log.warn("No device IDs found in database, skipping GPS update");
                            return Mono.just(List.<biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO>of());
                        }
                        log.debug("Found {} device IDs, fetching GPS positions", deviceIds.size());
                        return gpsApiClient.fetchVehiclePositionsByIds(deviceIds);
                    })
                    .doOnNext(positions -> log.debug("Fetched {} GPS positions, processing in batches of {}",
                            positions.size(), batchSize))
                    .flatMapIterable(positions -> positions)
                    .buffer(batchSize)
                    .parallel(parallelWorkers)
                    .runOn(Schedulers.parallel())
                    .flatMap(batch ->
                            updateVehiclePositionsUseCase.execute(batch)
                                    .timeout(batchTimeout)
                                    .onErrorResume(error -> {
                                        log.error("Failed to process GPS batch of {} vehicles: {} - {}",
                                                batch.size(),
                                                error.getClass().getSimpleName(),
                                                error.getMessage(),
                                                error);
                                        return Mono.just(VehiclePositionUpdateResult.failed(batch.size()));
                                    })
                    )
                    .sequential()
                    .reduce(new VehiclePositionUpdateResult(0, 0, 0, 0, 0, LocalDateTime.now(), List.of()),
                            VehiclePositionUpdateResult::merge)
                    .timeout(totalTimeout)
                    .publishOn(Schedulers.boundedElastic())
                    .doOnSuccess(result -> {
                        Duration duration = Duration.between(startTime, Instant.now());
                        log.info("GPS update completed: duration={}ms, updated={}, created={}, failed={}, invalid={}, conflict={}",
                                duration.toMillis(),
                                result.updatedCount(),
                                result.createdCount(),
                                result.failedCount(),
                                result.invalidCount(),
                                result.conflictCount());
                        saveGpsUpdateStats(result, duration).subscribe();
                    })
                    .doOnError(error -> {
                        Duration duration = Duration.between(startTime, Instant.now());
                        log.error("GPS update failed after {}ms: {}", duration.toMillis(), error.getMessage());
                        saveGpsUpdateError(error, duration).subscribe();
                    })
                    .onErrorResume(error -> Mono.empty())
                    .doFinally(signal -> {
                        gpsUpdateInProgress.set(false);
                        lastGpsUpdateStartTime = null;
                        log.info("GPS update finished with signal: {}", signal);
                    })
                    .subscribe();

        } catch (Exception e) {
            log.error("Unexpected error in GPS update scheduler", e);
            gpsUpdateInProgress.set(false);
            lastGpsUpdateStartTime = null;
        }
    }



    /*
     * ============================================================================
     * ЗАКОММЕНТИРОВАНО 2025-12-23: Bus Info API Sync
     * Причина: Переход на автономную систему назначения маршрутов
     *
     * Этот метод синхронизировал назначения маршрутов из внешней системы Имдат
     * каждые 2 минуты. Больше не используется.
     *
     * Альтернативы:
     * 1. VehicleShiftScheduler.applyFirstShift() / applySecondShift() - плановые назначения в 07:00 и 14:00
     * 2. GPS Auto-Detection - автоматическое определение по GPS (DetectRouteByGpsUseCase)
     * 3. Immediate Assignments - немедленное ручное назначение через админ панель
     *
     * Для восстановления: раскомментировать этот метод и поля выше.
     * ============================================================================
     */
    // @Scheduled(cron = "0 */2 * * * *")
    // public void syncBusRouteAssignments() {
    //     if (!busInfoSyncInProgress.compareAndSet(false, true)) {
    //         log.warn("Bus info sync already in progress, skipping this cycle");
    //         return;
    //     }
    //
    //     try {
    //         log.info("Starting scheduled bus route assignments sync");
    //         Instant startTime = Instant.now();
    //
    //         externalApiService.fetchAllBusInfo()
    //                 .flatMap(busInfos -> {
    //                     log.info("Fetched {} bus route assignments, processing...", busInfos.size());
    //                     return syncBusRouteAssignmentsUseCase.execute(busInfos);
    //                 })
    //                 .doOnSuccess(result -> {
    //                     Duration duration = Duration.between(startTime, Instant.now());
    //                     log.info("Bus route sync completed in {}ms: {}", duration.toMillis(), result);
    //                 })
    //                 .doOnError(error -> {
    //                     Duration duration = Duration.between(startTime, Instant.now());
    //                     log.error("Bus route sync failed after {}ms", duration.toMillis(), error);
    //                 })
    //                 .onErrorResume(error -> Mono.empty())
    //                 .doFinally(signal -> busInfoSyncInProgress.set(false))
    //                 .subscribe();
    //
    //     } catch (Exception e) {
    //         log.error("Unexpected error in bus route sync scheduler", e);
    //         busInfoSyncInProgress.set(false);
    //     }
    // }


    @Scheduled(cron = "0 * 6 * * *")
    public void checkExternalApisHealth() {

        Mono<Boolean> gpsHealth = externalApiService.gpsHealthCheck()
                .doOnNext(healthy -> log.debug("GPS API health: {}", healthy ? "OK" : "FAILED"))
                .flatMap(healthy -> saveHealthStatus(GPS_HEALTH_KEY, healthy).thenReturn(healthy));

        // ЗАКОММЕНТИРОВАНО 2025-12-23: Bus Info API больше не используется
        // Mono<Boolean> busInfoHealth = externalApiService.busInfoHealthCheck()
        //         .doOnNext(healthy -> log.debug("Bus Info API health: {}", healthy ? "OK" : "FAILED"))
        //         .flatMap(healthy -> saveHealthStatus(BUS_INFO_HEALTH_KEY, healthy).thenReturn(healthy));

        gpsHealth
                .publishOn(Schedulers.boundedElastic())
                .subscribe(
                        healthy -> log.debug("Health check completed: GPS={}", healthy),
                        error -> log.error("Health check failed", error)
                );
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
                .subscribe();

    }

    /*
     * ============================================================================
     * ЗАКОММЕНТИРОВАНО 2025-12-23: Initial Bus Info Sync при старте приложения
     * Причина: Больше не используем Bus Info API
     *
     * Эти методы запускали синхронизацию маршрутов из Имдат при старте приложения.
     *
     * Новое поведение:
     * - VehicleShiftScheduler автоматически применяет shift assignments в 07:00 и 14:00
     * - GPS автоопределение начинает работать сразу после запуска
     * - Не нужна начальная синхронизация с внешней системой
     * ============================================================================
     */
    // @EventListener(ApplicationReadyEvent.class)
    // public void onApplicationReady(ApplicationReadyEvent event) {
    //     log.info("Application ready - triggering initial route sync");
    //
    //     Mono.delay(Duration.ofSeconds(10))
    //             .then(Mono.defer(this::performInitialRouteSync))
    //             .subscribeOn(Schedulers.boundedElastic())
    //             .subscribe(
    //                     result -> log.info("Initial route sync completed: {}", result),
    //                     error -> log.error("Initial route sync failed", error)
    //             );
    // }
    //
    // private Mono<SyncBusRouteAssignmentsUseCase.BusRouteAssignmentResult> performInitialRouteSync() {
    //     log.info("Performing initial route synchronization...");
    //
    //     return externalApiService.fetchAllBusInfo()
    //             .flatMap(busInfos -> {
    //                 if (busInfos.isEmpty()) {
    //                     log.warn("No bus info available for initial sync");
    //                     return Mono.empty();
    //                 }
    //                 return syncBusRouteAssignmentsUseCase.execute(busInfos);
    //             })
    //             .doOnSuccess(result -> {
    //                 if (result != null && result.assignedCount() > 0) {
    //                     log.info("Initial route sync successful: assigned={}, unassigned={}, processedAt={}",
    //                             result.assignedCount(),
    //                             result.unassignedCount(),
    //                             result.processedAt());
    //                 } else {
    //                     log.info("Initial route sync: no assignments needed");
    //                 }
    //             });
    // }


    private Mono<Void> saveGpsUpdateStats(VehiclePositionUpdateResult result, Duration duration) {
        String key = GPS_UPDATE_STATS_KEY + ":" + Instant.now().getEpochSecond();

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

        return redisTemplate.opsForValue()
                .set(key, stats, Duration.ofDays(7))
                .then()
                .onErrorResume(error -> {
                    log.warn("Failed to save GPS stats to Redis: {}", error.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> saveGpsUpdateError(Throwable error, Duration duration) {
        String key = GPS_UPDATE_STATS_KEY + ":" + Instant.now().getEpochSecond();

        GpsUpdateStats stats = new GpsUpdateStats(
                0, 0, 0, 0, 0,
                duration.toMillis(),
                Instant.now(),
                false,
                error.getMessage()
        );

        return redisTemplate.opsForValue()
                .set(key, stats, Duration.ofDays(7))
                .then()
                .onErrorResume(redisError -> {
                    log.warn("Failed to save GPS error stats to Redis: {}", redisError.getMessage());
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