package biz.ugur.busroutebackend.transport.scheduler;

import biz.ugur.busroutebackend.transport.application.usecase.assignment.ProcessExpiredAssignmentsUseCase;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteSource;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VehicleShiftScheduler {

    public static final ZoneId ASHGABAT_ZONE = ZoneId.of("Asia/Ashgabat");
    private static final Duration TASK_TIMEOUT = Duration.ofMinutes(5);
    private static final String SHIFT_CHANGE_STATS_KEY = "shift:change:stats";
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final Duration RETRY_MIN_BACKOFF = Duration.ofMillis(50);
    private static final Duration RETRY_MAX_BACKOFF = Duration.ofMillis(500);

    private final RouteAssignmentRepository assignmentRepository;
    private final VehicleRepository vehicleRepository;
    private final BusRouteRepository busRouteRepository;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ProcessExpiredAssignmentsUseCase processExpiredAssignmentsUseCase;

    private final AtomicBoolean shiftChangeInProgress = new AtomicBoolean(false);

    public VehicleShiftScheduler(
            RouteAssignmentRepository assignmentRepository,
            VehicleRepository vehicleRepository,
            BusRouteRepository busRouteRepository,
            ReactiveRedisTemplate<String, Object> redisTemplate,
            ProcessExpiredAssignmentsUseCase processExpiredAssignmentsUseCase) {
        this.assignmentRepository = assignmentRepository;
        this.vehicleRepository = vehicleRepository;
        this.busRouteRepository = busRouteRepository;
        this.redisTemplate = redisTemplate;
        this.processExpiredAssignmentsUseCase = processExpiredAssignmentsUseCase;
    }

    @Scheduled(cron = "0 0 5 * * *", zone = "Asia/Ashgabat")
    public void applyFirstShift() {
        log.info("First shift starting at 05:00 (Ashgabat) - applying FIRST and FULL_DAY assignments");
        executeShiftChangeForMultipleTypes(List.of(ShiftType.FIRST, ShiftType.FULL_DAY), true);
    }

    @Scheduled(cron = "0 0 14 * * *", zone = "Asia/Ashgabat")
    public void applySecondShift() {
        log.info("Second shift starting at 14:00 (Ashgabat) - applying SECOND shift assignments");
        executeShiftChangeReactive(ShiftType.SECOND, true);
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Ashgabat")
    public void midnightCleanup() {
        ZonedDateTime now = ZonedDateTime.now(ASHGABAT_ZONE);
        LocalDate today = now.toLocalDate();

        log.info("Midnight cleanup starting at {} (Ashgabat) - deleting old assignments", now);

        executeMidnightCleanup(today);
    }

    @Scheduled(cron = "0 0 23 * * *", zone = "Asia/Ashgabat")
    public void endOfDayCleanup() {
        ZonedDateTime now = ZonedDateTime.now(ASHGABAT_ZONE);
        LocalDate today = now.toLocalDate();

        log.info("End of day cleanup starting at {} (Ashgabat) - deleting today's assignments", now);

        executeEndOfDayCleanup(today);
    }

    @Scheduled(cron = "0 0,30 * * * *", zone = "Asia/Ashgabat")
    public void cleanupExpiredAssignments() {
        log.debug("Backup cleanup: checking for expired assignments...");
        executeExpiredAssignmentsCleanup();
    }

    private void executeMidnightCleanup(LocalDate today) {
        assignmentRepository.deleteByEffectiveDateBefore(today)
                .timeout(TASK_TIMEOUT)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(deletedCount ->
                        log.info("Midnight cleanup completed: deleted {} old assignments (effective_date < {})",
                                deletedCount, today))
                .doOnError(error ->
                        log.error("Midnight cleanup failed: {}", error.getMessage(), error))
                .onErrorComplete()
                .subscribe();
    }

    private void executeEndOfDayCleanup(LocalDate today) {
        vehicleRepository.clearAllRouteAssignments()
                .then(assignmentRepository.deleteByEffectiveDate(today))
                .timeout(TASK_TIMEOUT)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(deletedCount ->
                        log.info("End of day cleanup completed: deleted {} assignments for date {}",
                                deletedCount, today))
                .doOnError(error ->
                        log.error("End of day cleanup failed: {}", error.getMessage(), error))
                .onErrorComplete()
                .subscribe();
    }

    private void executeExpiredAssignmentsCleanup() {
        Instant startTime = Instant.now();

        processExpiredAssignmentsUseCase.execute(Mono.empty())
                .timeout(TASK_TIMEOUT)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(result -> {
                    if (result != null && result.hasProcessed()) {
                        Duration duration = Duration.between(startTime, Instant.now());
                        log.info("Backup cleanup completed: processed={}, errors={}, duration={}ms",
                                result.processedCount(), result.errorCount(), duration.toMillis());
                    }
                })
                .doOnError(error ->
                        log.error("Backup cleanup failed: {}", error.getMessage(), error))
                .onErrorComplete()
                .subscribe();
    }

    private void executeShiftChangeReactive(ShiftType shiftType, boolean clearFirst) {
        executeShiftChangeForMultipleTypes(List.of(shiftType), clearFirst);
    }

    private void executeShiftChangeForMultipleTypes(List<ShiftType> shiftTypes, boolean clearFirst) {
        if (!shiftChangeInProgress.compareAndSet(false, true)) {
            log.warn("Shift change already in progress, skipping");
            Mono.<Void>empty().subscribe();
            return;
        }

        Instant startTime = Instant.now();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        LocalDate today = LocalDate.now(ASHGABAT_ZONE);

        Mono<Void> operation = clearFirst
                ? clearAndApplyAssignmentsForTypes(today, shiftTypes, successCount, failCount)
                : applyAssignmentsForTypes(today, shiftTypes, successCount, failCount);

        String shiftTypesStr = shiftTypes.stream().map(Enum::name).reduce((a, b) -> a + "," + b).orElse("");

        operation
                .timeout(TASK_TIMEOUT)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(unused -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    log.info("Shift change completed: shifts=[{}], duration={}ms, success={}, failed={}",
                            shiftTypesStr, duration.toMillis(), successCount.get(), failCount.get());
                })
                .doOnError(error -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    log.error("Shift change failed: shifts=[{}], duration={}ms, error={}",
                            shiftTypesStr, duration.toMillis(), error.getMessage(), error);
                })
                .onErrorComplete()
                .doFinally(signal -> {
                    shiftChangeInProgress.set(false);
                    Duration duration = Duration.between(startTime, Instant.now());
                    saveShiftChangeStats(shiftTypes.getFirst(), successCount.get(), failCount.get(), duration)
                            .onErrorComplete()
                            .subscribe();
                })
                .subscribe();
    }

    private Mono<Void> clearAndApplyAssignmentsForTypes(LocalDate today, List<ShiftType> shiftTypes,
                                                          AtomicInteger successCount, AtomicInteger failCount) {
        return getActiveAssignmentVehicleIdsForTypes(today, shiftTypes)
                .doOnNext(excludeIds ->
                        log.info("Found {} vehicles with active assignments to exclude from clearing", excludeIds.size()))
                .flatMap(vehicleRepository::clearRouteAssignmentsExcluding)
                .doOnNext(clearedCount ->
                        log.info("Cleared {} vehicle route assignments", clearedCount))
                .then(applyAssignmentsForTypes(today, shiftTypes, successCount, failCount));
    }

    private Mono<Void> applyAssignmentsForTypes(LocalDate today, List<ShiftType> shiftTypes,
                                                 AtomicInteger successCount, AtomicInteger failCount) {
        log.info("Applying assignments for shift types: {}", shiftTypes);

        return reactor.core.publisher.Flux.fromIterable(shiftTypes)
                .flatMap(shiftType -> assignmentRepository.findActiveByDateAndShift(today, shiftType))
                .flatMap(assignment -> applyAssignmentIfActive(assignment, successCount, failCount))
                .then();
    }

    private Mono<List<VehicleId>> getActiveAssignmentVehicleIdsForTypes(LocalDate date, List<ShiftType> shiftTypes) {
        return reactor.core.publisher.Flux.fromIterable(shiftTypes)
                .flatMap(shiftType -> assignmentRepository.findActiveByDateAndShift(date, shiftType))
                .filter(RouteAssignment::isCurrentlyValid)
                .map(RouteAssignment::getVehicleId)
                .distinct()
                .collectList();
    }

    private Mono<Vehicle> applyAssignmentIfActive(RouteAssignment assignment,
                                                   AtomicInteger successCount,
                                                   AtomicInteger failCount) {
        if (!assignment.isCurrentlyValid()) {
            log.debug("Skipping expired assignment: vehicleId={}, expiresAt={}",
                    assignment.getVehicleId(), assignment.getExpiresAt());
            return Mono.empty();
        }

        return vehicleRepository.findById(assignment.getVehicleId())
                .filter(vehicle -> Boolean.TRUE.equals(vehicle.getIsActive()))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Skipping assignment for inactive/not found vehicle: vehicleId={}",
                            assignment.getVehicleId());
                    return Mono.empty();
                }))
                .flatMap(vehicle -> applyAssignmentToVehicle(vehicle, assignment, successCount, failCount))
                .doOnError(error -> {
                    failCount.incrementAndGet();
                    log.error("Failed to assign vehicle {} to route: {}",
                            assignment.getVehicleId(), error.getMessage());
                })
                .onErrorResume(error -> Mono.empty());
    }


    private Mono<Vehicle> applyAssignmentToVehicle(Vehicle vehicle,
                                                    RouteAssignment assignment,
                                                    AtomicInteger successCount,
                                                    AtomicInteger failCount) {
        return Mono.defer(() -> fetchAndAssignVehicle(vehicle.getId(), assignment))
                .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_MIN_BACKOFF)
                        .maxBackoff(RETRY_MAX_BACKOFF)
                        .jitter(0.5)
                        .filter(throwable -> throwable instanceof OptimisticLockingFailureException)
                        .doBeforeRetry(signal -> log.debug(
                                "Retrying route assignment for vehicle {} (attempt {}/{})",
                                vehicle.getLicensePlate(), signal.totalRetries() + 1, MAX_RETRY_ATTEMPTS)))
                .doOnSuccess(v -> {
                    successCount.incrementAndGet();
                    log.debug("Assigned vehicle {} to route {} for {} shift on {}",
                            v.getLicensePlate(), v.getRouteNumber(),
                            assignment.getShiftType(), assignment.getEffectiveDate());
                });
    }

    private Mono<Vehicle> fetchAndAssignVehicle(VehicleId vehicleId, RouteAssignment assignment) {
        return vehicleRepository.findById(vehicleId)
                .flatMap(freshVehicle -> busRouteRepository.findById(assignment.getRouteId())
                        .map(route -> freshVehicle.toBuilder()
                                .assignedRouteId(assignment.getRouteId())
                                .routeNumber(route.getRouteNumber())
                                .routeSource(RouteSource.SHIFT_ASSIGNMENT)
                                .routeConfidence(100)
                                .build())
                        .defaultIfEmpty(freshVehicle.toBuilder()
                                .assignedRouteId(assignment.getRouteId())
                                .routeSource(RouteSource.SHIFT_ASSIGNMENT)
                                .routeConfidence(100)
                                .build())
                        .flatMap(vehicleRepository::save));
    }

    private Mono<Void> saveShiftChangeStats(ShiftType shiftType, int success, int failed, Duration duration) {
        String key = SHIFT_CHANGE_STATS_KEY + ":" + shiftType.name().toLowerCase() + ":" +
                     Instant.now().getEpochSecond();

        ShiftChangeStats stats = new ShiftChangeStats(
                shiftType.name(),
                success,
                failed,
                duration.toMillis(),
                LocalDateTime.now(),
                true
        );

        return redisTemplate.opsForValue()
                .set(key, stats, Duration.ofDays(7))
                .then()
                .onErrorResume(error -> {
                    log.warn("Failed to save shift change stats to Redis: {}", error.getMessage());
                    return Mono.empty();
                });
    }


    public record ShiftChangeStats(
            String shiftType,
            int successCount,
            int failedCount,
            long durationMs,
            LocalDateTime processedAt,
            boolean success
    ) {}
}
