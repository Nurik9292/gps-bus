package biz.ugur.busroutebackend.transport.scheduler;

import biz.ugur.busroutebackend.transport.application.usecase.immediate.ClearAllImmediateAssignmentsUseCase;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.model.VehicleShiftAssignment;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleShiftAssignmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.scheduling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class VehicleShiftScheduler {

    public static final ZoneId ASHGABAT_ZONE = ZoneId.of("Asia/Ashgabat");

    private final VehicleShiftAssignmentRepository shiftAssignmentRepository;
    private final ClearAllImmediateAssignmentsUseCase clearAllImmediateAssignmentsUseCase;
    private final VehicleRepository vehicleRepository;
    private final BusRouteRepository busRouteRepository;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    private final AtomicBoolean shiftChangeInProgress = new AtomicBoolean(false);

    private static final String SHIFT_CHANGE_STATS_KEY = "shift:change:stats";

    public VehicleShiftScheduler(
            VehicleShiftAssignmentRepository shiftAssignmentRepository,
            ClearAllImmediateAssignmentsUseCase clearAllImmediateAssignmentsUseCase,
            VehicleRepository vehicleRepository,
            BusRouteRepository busRouteRepository,
            ReactiveRedisTemplate<String, Object> redisTemplate) {
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.clearAllImmediateAssignmentsUseCase = clearAllImmediateAssignmentsUseCase;
        this.vehicleRepository = vehicleRepository;
        this.busRouteRepository = busRouteRepository;
        this.redisTemplate = redisTemplate;
    }


    @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Ashgabat")
    public void applyFirstShift() {
        log.info("First shift starting at 07:00 (Ashgabat) - applying shift assignments");
        applyShiftAssignments(ShiftType.FIRST);
    }


    @Scheduled(cron = "0 0 14 * * *", zone = "Asia/Ashgabat")
    public void applySecondShift() {
        log.info("Second shift starting at 14:00 (Ashgabat) - applying shift assignments");
        applyShiftAssignments(ShiftType.SECOND);
    }


    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Ashgabat")
    public void midnightReset() {
        ZonedDateTime now = ZonedDateTime.now(ASHGABAT_ZONE);
        log.info("Midnight reset starting at {} (Ashgabat) - clearing all immediate assignments", now);

        clearAllImmediateAssignmentsUseCase.execute(null)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnSuccess(result -> log.info("Midnight reset completed: cleared={} assignments", result.clearedCount()))
                .doOnError(error -> log.error("Midnight reset failed: {}", error.getMessage()))
                .subscribe();
    }

    public void applyShiftAssignments(ShiftType shiftType) {
        if (!shiftChangeInProgress.compareAndSet(false, true)) {
            log.warn("Shift change already in progress, skipping");
            return;
        }

        Instant startTime = Instant.now();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        log.info("Applying {} shift assignments", shiftType);

        shiftAssignmentRepository.findActiveByShiftType(shiftType)
                .flatMap(assignment -> applyAssignment(assignment, successCount, failCount))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnComplete(() -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    log.info("Shift change completed: shift={}, duration={}ms, success={}, failed={}",
                            shiftType, duration.toMillis(), successCount.get(), failCount.get());
                    saveShiftChangeStats(shiftType, successCount.get(), failCount.get(), duration).subscribe();
                })
                .doOnError(error -> {
                    Duration duration = Duration.between(startTime, Instant.now());
                    log.error("Shift change failed: shift={}, duration={}ms, error={}",
                            shiftType, duration.toMillis(), error.getMessage());
                })
                .doFinally(signal -> shiftChangeInProgress.set(false))
                .subscribe();
    }

    private Mono<Vehicle> applyAssignment(VehicleShiftAssignment assignment,
                                          AtomicInteger successCount,
                                          AtomicInteger failCount) {
        return vehicleRepository.findById(assignment.getVehicleId())
                .flatMap(vehicle -> {
                    Vehicle updatedVehicle = vehicle.assignToRoute(assignment.getRouteId());

                    return busRouteRepository.findById(assignment.getRouteId())
                            .map(route -> updatedVehicle.updateCachedRouteNumber(route.getRouteNumber()))
                            .defaultIfEmpty(updatedVehicle);
                })
                .flatMap(vehicleRepository::save)
                .doOnSuccess(v -> {
                    successCount.incrementAndGet();
                    log.debug("Assigned vehicle {} to route {} for {} shift",
                            v.getLicensePlate(), v.getRouteNumber(), assignment.getShiftType());
                })
                .doOnError(error -> {
                    failCount.incrementAndGet();
                    log.error("Failed to assign vehicle {} to route: {}",
                            assignment.getVehicleId(), error.getMessage());
                })
                .onErrorResume(error -> Mono.empty());
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
        log.info("Manually applying current shift: {}", currentShift);

        if (!shiftChangeInProgress.compareAndSet(false, true)) {
            return Mono.just(new ShiftChangeResult(currentShift.name(), 0, 0, 0, false, "Shift change already in progress"));
        }

        Instant startTime = Instant.now();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        return shiftAssignmentRepository.findActiveByShiftType(currentShift)
                .flatMap(assignment -> applyAssignment(assignment, successCount, failCount))
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
                .onErrorReturn(new ShiftChangeResult(currentShift.name(), successCount.get(), failCount.get(), 0, false, "Error occurred"))
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
