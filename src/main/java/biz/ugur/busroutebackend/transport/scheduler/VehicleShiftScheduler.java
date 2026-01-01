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
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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

    private final RouteAssignmentRepository assignmentRepository;
    private final VehicleRepository vehicleRepository;
    private final BusRouteRepository busRouteRepository;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ProcessExpiredAssignmentsUseCase processExpiredAssignmentsUseCase;

    private final AtomicBoolean shiftChangeInProgress = new AtomicBoolean(false);

    private static final String SHIFT_CHANGE_STATS_KEY = "shift:change:stats";

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

    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Ashgabat")
    public void applyFirstShift() {
        log.info("First shift starting at 07:00 (Ashgabat) - applying FIRST shift assignments");
        clearAndApplyShiftAssignments(ShiftType.FIRST);
    }

    @Scheduled(cron = "0 0 14 * * *", zone = "Asia/Ashgabat")
    public void applySecondShift() {
        log.info("Second shift starting at 14:00 (Ashgabat) - applying SECOND shift assignments");
        clearAndApplyShiftAssignments(ShiftType.SECOND);
    }


    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Ashgabat")
    public void midnightCleanup() {
        ZonedDateTime now = ZonedDateTime.now(ASHGABAT_ZONE);
        LocalDate today = now.toLocalDate();

        log.info("Midnight cleanup starting at {} (Ashgabat) - deleting old assignments", now);

        assignmentRepository.deleteByEffectiveDateBefore(today)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(deletedCount ->
                    log.info("Midnight cleanup completed: deleted {} old assignments (effective_date < {})",
                            deletedCount, today))
                .subscribe(
                        deletedCount -> {},
                        error -> log.error("Midnight cleanup failed: {}", error.getMessage())
                );
    }


    @Scheduled(cron = "0 0,30 * * * *", zone = "Asia/Ashgabat")
    public void cleanupExpiredAssignments() {
        log.debug("Backup cleanup: checking for expired assignments...");

        Instant startTime = Instant.now();

        processExpiredAssignmentsUseCase.execute(Mono.empty())
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(result -> {
                    if (result.hasProcessed()) {
                        Duration duration = Duration.between(startTime, Instant.now());
                        log.info("Backup cleanup completed: processed={}, errors={}, duration={}ms",
                                result.processedCount(), result.errorCount(), duration.toMillis());
                    }
                })
                .subscribe(
                        result -> {},
                        error -> log.error("Backup cleanup failed: {}", error.getMessage())
                );
    }

    public void clearAndApplyShiftAssignments(ShiftType shiftType) {
        if (!shiftChangeInProgress.compareAndSet(false, true)) {
            log.warn("Shift change already in progress, skipping");
            return;
        }

        Instant startTime = Instant.now();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        LocalDate today = LocalDate.now(ASHGABAT_ZONE);

        log.info("Clearing route assignments before applying {} shift (preserving vehicles with active assignments)",
                shiftType);

        getActiveAssignmentVehicleIds(today, shiftType)
                .flatMap(excludeVehicleIds -> {
                    log.info("Found {} vehicles with active assignments to exclude from clearing",
                            excludeVehicleIds.size());
                    return vehicleRepository.clearRouteAssignmentsExcluding(excludeVehicleIds);
                })
                .doOnSuccess(clearedCount -> log.info("Cleared {} vehicle route assignments", clearedCount))
                .thenMany(assignmentRepository.findActiveByDateAndShift(today, shiftType))
                .flatMap(assignment -> applyAssignmentIfActive(assignment, successCount, failCount))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnComplete(() -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    log.info("Shift change completed: shift={}, duration={}ms, success={}, failed={}",
                            shiftType, duration.toMillis(), successCount.get(), failCount.get());
                    saveShiftChangeStats(shiftType, successCount.get(), failCount.get(), duration).subscribe(
                            unused -> {},
                            err -> log.warn("Failed to save shift change stats: {}", err.getMessage())
                    );
                })
                .doFinally(signal -> shiftChangeInProgress.set(false))
                .subscribe(
                        vehicle -> {},
                        error -> {
                            Duration duration = Duration.between(startTime, Instant.now());
                            log.error("Shift change failed: shift={}, duration={}ms, error={}",
                                    shiftType, duration.toMillis(), error.getMessage());
                        }
                );
    }


    private Mono<List<VehicleId>> getActiveAssignmentVehicleIds(LocalDate date, ShiftType shiftType) {
        return assignmentRepository.findActiveByDateAndShift(date, shiftType)
                .filter(RouteAssignment::isCurrentlyValid)
                .map(RouteAssignment::getVehicleId)
                .collectList();
    }


    public void applyShiftAssignments(ShiftType shiftType) {
        if (!shiftChangeInProgress.compareAndSet(false, true)) {
            log.warn("Shift change already in progress, skipping");
            return;
        }

        Instant startTime = Instant.now();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        LocalDate today = LocalDate.now(ASHGABAT_ZONE);

        log.info("Applying {} shift assignments (without clearing)", shiftType);

        assignmentRepository.findActiveByDateAndShift(today, shiftType)
                .flatMap(assignment -> applyAssignmentIfActive(assignment, successCount, failCount))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnComplete(() -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    log.info("Shift application completed: shift={}, duration={}ms, success={}, failed={}",
                            shiftType, duration.toMillis(), successCount.get(), failCount.get());
                    saveShiftChangeStats(shiftType, successCount.get(), failCount.get(), duration).subscribe(
                            unused -> {},
                            err -> log.warn("Failed to save shift change stats: {}", err.getMessage())
                    );
                })
                .doFinally(signal -> shiftChangeInProgress.set(false))
                .subscribe(
                        vehicle -> {},
                        error -> {
                            Duration duration = Duration.between(startTime, Instant.now());
                            log.error("Shift application failed: shift={}, duration={}ms, error={}",
                                    shiftType, duration.toMillis(), error.getMessage());
                        }
                );
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
        return busRouteRepository.findById(assignment.getRouteId())
                .map(route -> vehicle.toBuilder()
                        .assignedRouteId(assignment.getRouteId())
                        .routeNumber(route.getRouteNumber())
                        .routeSource(RouteSource.SHIFT_ASSIGNMENT)
                        .routeConfidence(100)
                        .build())
                .defaultIfEmpty(vehicle.toBuilder()
                        .assignedRouteId(assignment.getRouteId())
                        .routeSource(RouteSource.SHIFT_ASSIGNMENT)
                        .routeConfidence(100)
                        .build())
                .flatMap(vehicleRepository::save)
                .doOnSuccess(v -> {
                    successCount.incrementAndGet();
                    log.debug("Assigned vehicle {} to route {} for {} shift on {}",
                            v.getLicensePlate(), v.getRouteNumber(),
                            assignment.getShiftType(), assignment.getEffectiveDate());
                });
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


    public Mono<ShiftChangeResult> applyCurrentShift() {
        ShiftType currentShift = ShiftType.getCurrentShift();
        log.info("Manually applying current shift: {} (with clear, preserving active assignments)", currentShift);

        if (!shiftChangeInProgress.compareAndSet(false, true)) {
            return Mono.just(new ShiftChangeResult(
                    currentShift.name(), 0, 0, 0, false,
                    "Shift change already in progress"));
        }

        Instant startTime = Instant.now();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        LocalDate today = LocalDate.now(ASHGABAT_ZONE);

        return getActiveAssignmentVehicleIds(today, currentShift)
                .flatMap(excludeVehicleIds -> {
                    log.info("Found {} vehicles with active assignments to exclude from clearing",
                            excludeVehicleIds.size());
                    return vehicleRepository.clearRouteAssignmentsExcluding(excludeVehicleIds);
                })
                .doOnSuccess(clearedCount ->
                    log.info("Cleared {} vehicle route assignments before manual shift apply", clearedCount))
                .thenMany(assignmentRepository.findActiveByDateAndShift(today, currentShift))
                .flatMap(assignment -> applyAssignmentIfActive(assignment, successCount, failCount))
                .then(Mono.fromSupplier(() -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    return new ShiftChangeResult(
                            currentShift.name(),
                            successCount.get(),
                            failCount.get(),
                            duration.toMillis(),
                            true,
                            null
                    );
                }))
                .doOnError(error -> log.error("Manual shift change failed", error))
                .onErrorReturn(new ShiftChangeResult(
                        currentShift.name(), successCount.get(), failCount.get(),
                        0, false, "Error occurred"))
                .doFinally(signal -> shiftChangeInProgress.set(false));
    }


    public record ShiftChangeStats(
            String shiftType,
            int successCount,
            int failedCount,
            long durationMs,
            LocalDateTime processedAt,
            boolean success
    ) {}


    public record ShiftChangeResult(
            String shiftType,
            int assignedCount,
            int failedCount,
            long durationMs,
            boolean success,
            String errorMessage
    ) {}
}
