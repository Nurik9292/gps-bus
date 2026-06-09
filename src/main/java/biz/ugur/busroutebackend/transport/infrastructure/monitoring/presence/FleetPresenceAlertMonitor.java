package biz.ugur.busroutebackend.transport.infrastructure.monitoring.presence;

import biz.ugur.busroutebackend.shared.infrastructure.email.EmailNotificationService;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring.AlertKind;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring.GpsAlertProperties;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import biz.ugur.busroutebackend.transport.infrastructure.monitoring.offroute.OffRouteRecord;
import biz.ugur.busroutebackend.transport.infrastructure.monitoring.offroute.OffRouteStateRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.fleet-presence-alerts", name = "enabled", havingValue = "true")
public class FleetPresenceAlertMonitor {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Duration TASK_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration PER_ITEM_TIMEOUT = Duration.ofSeconds(10);
    private static final int CONCURRENCY = 8;

    private final EmailNotificationService emailService;
    private final FleetPresenceAlertProperties properties;
    private final GpsAlertProperties gpsAlertProperties;
    private final RouteAssignmentRepository assignmentRepository;
    private final VehicleRepository vehicleRepository;
    private final BusRouteRepository busRouteRepository;
    private final OffRouteStateRegistry offRouteStateRegistry;
    private final Clock clock;

    private final AtomicBoolean inProgress = new AtomicBoolean(false);
    private volatile String lastSentHash = null;
    private volatile Instant lastSentAt = null;

    public FleetPresenceAlertMonitor(EmailNotificationService emailService,
                                     FleetPresenceAlertProperties properties,
                                     GpsAlertProperties gpsAlertProperties,
                                     RouteAssignmentRepository assignmentRepository,
                                     VehicleRepository vehicleRepository,
                                     BusRouteRepository busRouteRepository,
                                     OffRouteStateRegistry offRouteStateRegistry,
                                     Clock clock) {
        this.emailService = emailService;
        this.properties = properties;
        this.gpsAlertProperties = gpsAlertProperties;
        this.assignmentRepository = assignmentRepository;
        this.vehicleRepository = vehicleRepository;
        this.busRouteRepository = busRouteRepository;
        this.offRouteStateRegistry = offRouteStateRegistry;
        this.clock = clock;
    }

    @Scheduled(fixedRateString = "${app.fleet-presence-alerts.check-interval-minutes:10}", timeUnit = TimeUnit.MINUTES)
    public void scheduledCheck() {
        if (!inProgress.compareAndSet(false, true)) {
            log.debug("[FLEET_PRESENCE] previous check still running, skipping");
            return;
        }
        checkNow()
                .timeout(TASK_TIMEOUT)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(err -> log.error("[FLEET_PRESENCE] check failed: {}", err.toString()))
                .onErrorComplete()
                .doFinally(sig -> inProgress.set(false))
                .subscribe();
    }

    public Mono<Void> checkNow() {
        Instant now = clock.instant();
        LocalTime localTime = LocalTime.ofInstant(now, ZoneOffset.UTC);
        Optional<ShiftType> shiftOpt = currentShift(localTime);
        if (shiftOpt.isEmpty()) {
            return Mono.empty();
        }
        ShiftType shift = shiftOpt.get();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        LocalDateTime nowLocal = LocalDateTime.ofInstant(now, ZoneOffset.UTC);

        return assignedVehicleIds(today, shift)
                .flatMap(vehicleId -> toProblem(vehicleId, today, shift, nowLocal, now), CONCURRENCY)
                .collectList()
                .flatMap(problems -> dispatchIfNeeded(problems, shift, now));
    }

    private Flux<VehicleId> assignedVehicleIds(LocalDate today, ShiftType shift) {
        return Flux.concat(
                        assignmentRepository.findActiveByDateAndShift(today, shift),
                        assignmentRepository.findActiveByDateAndShift(today, ShiftType.FULL_DAY))
                .filter(RouteAssignment::isCurrentlyValid)
                .map(RouteAssignment::getVehicleId)
                .distinct();
    }

    private Mono<FleetProblem> toProblem(VehicleId vehicleId, LocalDate today, ShiftType shift,
                                         LocalDateTime nowLocal, Instant now) {
        return vehicleRepository.findById(vehicleId)
                .timeout(PER_ITEM_TIMEOUT)
                .filter(v -> Boolean.TRUE.equals(v.getIsActive()))
                .flatMap(v -> {
                    Optional<OffRouteRecord> offRoute =
                            offRouteStateRegistry.find(vehicleId.getValue(), today, shift);
                    Optional<AssignedVehicleStatus> status =
                            classify(v.getLastPositionUpdate(), offRoute, nowLocal, shift, properties);
                    return status.map(s -> Mono.just(buildProblem(vehicleId, v, s, offRoute, now)))
                            .orElseGet(Mono::empty);
                })
                .onErrorResume(err -> {
                    log.warn("[FLEET_PRESENCE] classify failed for {}: {}", vehicleId, err.toString());
                    return Mono.empty();
                });
    }

    private FleetProblem buildProblem(VehicleId vehicleId, Vehicle v, AssignedVehicleStatus status,
                                      Optional<OffRouteRecord> offRoute, Instant now) {
        String lastSignal = v.getLastPositionUpdate() != null
                ? humanDuration(Duration.between(
                        v.getLastPositionUpdate().toInstant(ZoneOffset.UTC), now)) + " назад"
                : "никогда сегодня";
        String distance = offRoute.map(r -> String.valueOf((int) Math.round(r.distanceMeters()))).orElse("-");
        String plate = v.getLicensePlate() != null ? v.getLicensePlate() : vehicleId.getValue();
        String route = v.getRouteNumber() != null ? v.getRouteNumber() : "-";
        return new FleetProblem(vehicleId.getValue(), plate, route, status, lastSignal, distance);
    }

    private Mono<Void> dispatchIfNeeded(List<FleetProblem> problems, ShiftType shift, Instant now) {
        if (problems.isEmpty()) {
            return Mono.empty();
        }
        String hash = hashOf(problems);
        boolean changed = !hash.equals(lastSentHash);
        boolean cooldownPassed = lastSentAt == null
                || Duration.between(lastSentAt, now).toMinutes() >= properties.getMinResendCooldownMinutes();
        if (!changed || !cooldownPassed) {
            log.debug("[FLEET_PRESENCE] {} problems, suppressed (changed={}, cooldownPassed={})",
                    problems.size(), changed, cooldownPassed);
            return Mono.empty();
        }

        String subject = "[FLEET] " + problems.size() + " автобусов не на линии (смена " + shift.name() + ")";
        String body = "<pre>" + renderBody(problems, shift, now) + "</pre>";
        log.warn("[FLEET_PRESENCE] dispatching summary: {} vehicles, shift={}", problems.size(), shift);

        return emailService.sendGpsAlert(gpsAlertProperties.recipientList(), "ASSIGNED_FLEET",
                        AlertKind.ASSIGNED_NOT_ON_LINE, subject, body)
                .doOnSuccess(ignored -> {
                    lastSentHash = hash;
                    lastSentAt = now;
                })
                .onErrorResume(err -> {
                    log.error("[FLEET_PRESENCE] dispatch failed: {}", err.toString());
                    return Mono.empty();
                });
    }

    private Optional<ShiftType> currentShift(LocalTime localTime) {
        return Arrays.stream(ShiftType.values())
                .filter(s -> s != ShiftType.FULL_DAY)
                .filter(s -> s.isActiveAt(localTime))
                .findFirst();
    }

    private String hashOf(List<FleetProblem> problems) {
        return problems.stream()
                .map(p -> p.vehicleId() + ":" + p.status().name())
                .sorted()
                .reduce("", (a, b) -> a + "|" + b);
    }

    private String renderBody(List<FleetProblem> problems, ShiftType shift, Instant now) {
        List<String> rows = new ArrayList<>();
        for (FleetProblem p : problems) {
            rows.add(p.licensePlate() + " | " + p.routeNumber() + " | " + shift.name() + " | "
                    + p.status().name() + " | " + p.lastSignalAgo() + " | " + p.distanceMeters());
        }
        String template = loadTemplate();
        return template
                .replace("{shift}", shift.name())
                .replace("{detectedAt}", formatInstant(now))
                .replace("{count}", String.valueOf(problems.size()))
                .replace("{rows}", String.join("\n", rows));
    }

    private String loadTemplate() {
        try (var is = getClass().getResourceAsStream("/email-templates/assigned-not-on-line.txt")) {
            if (is == null) {
                return "Назначенные автобусы не на линии:\n{rows}";
            }
            return new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            log.error("[FLEET_PRESENCE] template load failed: {}", e.getMessage());
            return "Назначенные автобусы не на линии:\n{rows}";
        }
    }

    private String formatInstant(Instant t) {
        return OffsetDateTime.ofInstant(t, ZoneOffset.UTC).format(TS_FMT);
    }

    private String humanDuration(Duration d) {
        long h = d.toHours();
        long m = d.toMinutesPart();
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("ч ");
        sb.append(m).append("м");
        return sb.toString();
    }

    static Optional<AssignedVehicleStatus> classify(LocalDateTime lastPositionUpdate,
                                                    Optional<OffRouteRecord> offRoute,
                                                    LocalDateTime nowLocal,
                                                    ShiftType shift,
                                                    FleetPresenceAlertProperties props) {
        if (offRoute.isPresent()) {
            return Optional.of(AssignedVehicleStatus.OFF_ROUTE);
        }

        LocalDateTime shiftStart = nowLocal.toLocalDate().atTime(shift.getStartTime());

        if (lastPositionUpdate == null || lastPositionUpdate.isBefore(shiftStart)) {
            long minutesSinceShiftStart = Duration.between(shiftStart, nowLocal).toMinutes();
            if (minutesSinceShiftStart < props.getStartupGraceMinutes()) {
                return Optional.empty();
            }
            return Optional.of(AssignedVehicleStatus.NOT_STARTED);
        }

        LocalDateTime freshCutoff = nowLocal.minusMinutes(props.getSilentThresholdMinutes());
        if (lastPositionUpdate.isAfter(freshCutoff)) {
            return Optional.empty();
        }
        return Optional.of(AssignedVehicleStatus.WENT_SILENT);
    }

    public record FleetProblem(String vehicleId, String licensePlate, String routeNumber,
                               AssignedVehicleStatus status, String lastSignalAgo, String distanceMeters) {
    }
}
