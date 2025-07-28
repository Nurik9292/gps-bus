package biz.ugur.busroutebackend.transport.scheduler;

import biz.ugur.busroutebackend.shared.infrastructure.external.BusInfoApiClient;
import biz.ugur.busroutebackend.shared.infrastructure.external.GpsApiClient;
import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionUpdateResult;
import biz.ugur.busroutebackend.transport.application.usecase.SyncBusRouteAssignmentsUseCase;
import biz.ugur.busroutebackend.transport.application.usecase.UpdateVehiclePositionsUseCase;
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
import java.util.concurrent.atomic.AtomicBoolean;


@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VehicleDataScheduler {

    private final GpsApiClient gpsApiClient;
    private final BusInfoApiClient busInfoApiClient;
    private final UpdateVehiclePositionsUseCase updateVehiclePositionsUseCase;
    private final SyncBusRouteAssignmentsUseCase syncBusRouteAssignmentsUseCase;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    private final AtomicBoolean gpsUpdateInProgress = new AtomicBoolean(false);
    private final AtomicBoolean busInfoSyncInProgress = new AtomicBoolean(false);

    private static final String GPS_UPDATE_STATS_KEY = "gps:update:stats";
    private static final String GPS_HEALTH_KEY = "gps:health";
    private static final String BUS_INFO_HEALTH_KEY = "bus_info:health";

    public VehicleDataScheduler(GpsApiClient gpsApiClient,
                                BusInfoApiClient busInfoApiClient,
                                UpdateVehiclePositionsUseCase updateVehiclePositionsUseCase,
                                SyncBusRouteAssignmentsUseCase syncBusRouteAssignmentsUseCase,
                                ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.gpsApiClient = gpsApiClient;
        this.busInfoApiClient = busInfoApiClient;
        this.updateVehiclePositionsUseCase = updateVehiclePositionsUseCase;
        this.syncBusRouteAssignmentsUseCase = syncBusRouteAssignmentsUseCase;
        this.redisTemplate = redisTemplate;
    }


    @Scheduled(cron = "0,30 * * * * *")
    public void updateVehiclePositions() {
        if (!gpsUpdateInProgress.compareAndSet(false, true)) {
            log.warn("GPS update already in progress, skipping this cycle");
            return;
        }

        try {
            log.debug("Starting scheduled GPS positions update");
            Instant startTime = Instant.now();

            gpsApiClient.fetchAllVehiclePositions()
                    .flatMap(positions -> {
                        log.info("Fetched {} GPS positions, processing...", positions.size());
                        return updateVehiclePositionsUseCase.execute(positions);
                    })
                    .publishOn(Schedulers.boundedElastic())
                    .doOnSuccess(result -> {
                        Duration duration = Duration.between(startTime, Instant.now());
                        log.info("GPS update completed in {}ms: {}", duration.toMillis(), result);

                        saveGpsUpdateStats(result, duration).subscribe();
                    })
                    .doOnError(error -> {
                        Duration duration = Duration.between(startTime, Instant.now());
                        log.error("GPS update failed after {}ms", duration.toMillis(), error);

                        saveGpsUpdateError(error, duration).subscribe();
                    })
                    .doFinally(signal -> gpsUpdateInProgress.set(false))
                    .subscribe();

        } catch (Exception e) {
            log.error("Unexpected error in GPS update scheduler", e);
            gpsUpdateInProgress.set(false);
        }
    }


    @Scheduled(cron = "0 * * * * *")
    public void syncBusRouteAssignments() {
        log.info("🔥🔥🔥 SCHEDULER TRIGGERED! Current time: {}", Instant.now());
        if (!busInfoSyncInProgress.compareAndSet(false, true)) {
            log.warn("Bus info sync already in progress, skipping this cycle");
            return;
        }

        try {
            log.info("Starting scheduled bus route assignments sync");
            Instant startTime = Instant.now();

            busInfoApiClient.fetchAllBusInfo()
                    .flatMap(busInfos -> {
                        log.info("Fetched {} bus route assignments, processing...", busInfos.size());
                        return syncBusRouteAssignmentsUseCase.execute(busInfos);
                    })
                    .doOnSuccess(result -> {
                        Duration duration = Duration.between(startTime, Instant.now());
                        log.info("Bus route sync completed in {}ms: {}", duration.toMillis(), result);
                    })
                    .doOnError(error -> {
                        Duration duration = Duration.between(startTime, Instant.now());
                        log.error("Bus route sync failed after {}ms", duration.toMillis(), error);
                    })
                    .doFinally(signal -> busInfoSyncInProgress.set(false))
                    .subscribe();

        } catch (Exception e) {
            log.error("Unexpected error in bus route sync scheduler", e);
            busInfoSyncInProgress.set(false);
        }
    }


    @Scheduled(cron = "* * 6 * * *")
    public void checkExternalApisHealth() {
        log.debug("Checking external APIs health");

        gpsApiClient.healthCheck()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(healthy -> {
                    log.debug("GPS API health: {}", healthy ? "OK" : "FAILED");
                    saveHealthStatus(GPS_HEALTH_KEY, healthy).subscribe();
                })
                .onErrorResume(error -> {
                    log.warn("GPS API health check failed", error);
                    return saveHealthStatus(GPS_HEALTH_KEY, false).thenReturn(false);
                })
                .subscribe();

        busInfoApiClient.healthCheck()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(healthy -> {
                    log.debug("Bus Info API health: {}", healthy ? "OK" : "FAILED");
                    saveHealthStatus(BUS_INFO_HEALTH_KEY, healthy).subscribe();
                })
                .onErrorResume(error -> {
                    log.warn("Bus Info API health check failed", error);
                    return saveHealthStatus(BUS_INFO_HEALTH_KEY, false).thenReturn(false);
                })
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
                .subscribe();

        // Можно добавить очистку других кэшированных данных
    }


    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        log.info("🚀 Application ready - triggering initial route sync");

        Mono.delay(Duration.ofSeconds(10))
                .then(Mono.defer(this::performInitialRouteSync))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        result -> log.info("✅ Initial route sync completed: {}", result),
                        error -> log.error("❌ Initial route sync failed", error)
                );
    }

    private Mono<SyncBusRouteAssignmentsUseCase.BusRouteAssignmentResult> performInitialRouteSync() {
        log.info("🔄 Performing initial route synchronization...");

        return busInfoApiClient.fetchAllBusInfo()
                .flatMap(busInfos -> {
                    if (busInfos.isEmpty()) {
                        log.warn("⚠️ No bus info available for initial sync");
                        return Mono.empty();
                    }

                    log.info("📡 Initial sync: processing {} bus assignments", busInfos.size());
                    return syncBusRouteAssignmentsUseCase.execute(busInfos);
                })
                .doOnSuccess(result -> {
                    if (result != null && result.assignedCount() > 0) {
                        log.info("🎯 Initial route sync successful: {} vehicles assigned to routes",
                                result.assignedCount());
                    } else {
                        log.info("ℹ️ Initial route sync: no assignments needed");
                    }
                });
    }



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
                .then();
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
                .then();
    }

    private Mono<Void> saveHealthStatus(String key, boolean healthy) {
        HealthStatus status = new HealthStatus(healthy, Instant.now());

        return redisTemplate.opsForValue()
                .set(key, status, Duration.ofMinutes(10))
                .then();
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


    public static class GpsUpdateStats {
        public long updatedCount;
        public long createdCount;
        public long failedCount;
        public long invalidCount;
        public long conflictCount;
        public long durationMs;
        public Instant timestamp;
        public boolean successful;
        public String errorMessage;

        public GpsUpdateStats() {}

        public GpsUpdateStats(long updatedCount, long createdCount, long failedCount,
                              long invalidCount, long conflictCount, long durationMs,
                              Instant timestamp, boolean successful) {
            this(updatedCount, createdCount, failedCount, invalidCount, conflictCount,
                    durationMs, timestamp, successful, null);
        }

        public GpsUpdateStats(long updatedCount, long createdCount, long failedCount,
                              long invalidCount, long conflictCount, long durationMs,
                              Instant timestamp, boolean successful, String errorMessage) {
            this.updatedCount = updatedCount;
            this.createdCount = createdCount;
            this.failedCount = failedCount;
            this.invalidCount = invalidCount;
            this.conflictCount = conflictCount;
            this.durationMs = durationMs;
            this.timestamp = timestamp;
            this.successful = successful;
            this.errorMessage = errorMessage;
        }
    }

    public static class HealthStatus {
        public boolean healthy;
        public Instant lastCheck;

        public HealthStatus() {}

        public HealthStatus(boolean healthy, Instant lastCheck) {
            this.healthy = healthy;
            this.lastCheck = lastCheck;
        }
    }
}