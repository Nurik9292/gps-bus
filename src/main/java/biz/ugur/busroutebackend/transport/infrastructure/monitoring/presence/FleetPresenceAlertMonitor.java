package biz.ugur.busroutebackend.transport.infrastructure.monitoring.presence;

import biz.ugur.busroutebackend.shared.infrastructure.email.EmailNotificationService;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring.AlertKind;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring.GpsAlertProperties;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.fleet-presence-alerts", name = "enabled", havingValue = "true")
public class FleetPresenceAlertMonitor {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Duration TASK_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration PER_ITEM_TIMEOUT = Duration.ofSeconds(10);
    private static final int CONCURRENCY = 8;

    private final EmailNotificationService emailService;
    private final biz.ugur.busroutebackend.shared.infrastructure.email.AlertQuietHours quietHours;
    private final FleetPresenceAlertProperties properties;
    private final GpsAlertProperties gpsAlertProperties;
    private final RouteAssignmentRepository assignmentRepository;
    private final VehicleRepository vehicleRepository;
    private final BusRouteRepository busRouteRepository;
    private final OffRouteStateRegistry offRouteStateRegistry;
    private final Clock clock;
    private final String emailTemplate;
    private final String emptyRoutesTemplate;
    private final String unassignedTemplate;

    private final AtomicBoolean inProgress = new AtomicBoolean(false);
    private volatile String lastSentHash = null;
    private volatile Instant lastSentAt = null;

    public FleetPresenceAlertMonitor(EmailNotificationService emailService,
                                     biz.ugur.busroutebackend.shared.infrastructure.email.AlertQuietHours quietHours,
                                     FleetPresenceAlertProperties properties,
                                     GpsAlertProperties gpsAlertProperties,
                                     RouteAssignmentRepository assignmentRepository,
                                     VehicleRepository vehicleRepository,
                                     BusRouteRepository busRouteRepository,
                                     OffRouteStateRegistry offRouteStateRegistry,
                                     Clock clock) {
        this.emailService = emailService;
        this.quietHours = quietHours;
        this.properties = properties;
        this.gpsAlertProperties = gpsAlertProperties;
        this.assignmentRepository = assignmentRepository;
        this.vehicleRepository = vehicleRepository;
        this.busRouteRepository = busRouteRepository;
        this.offRouteStateRegistry = offRouteStateRegistry;
        this.clock = clock;
        this.emailTemplate = loadTemplate("/email-templates/assigned-not-on-line.txt",
                "Назначенные автобусы не на линии:\n{rows}");
        this.emptyRoutesTemplate = loadTemplate("/email-templates/empty-routes.txt",
                "Маршруты без автобусов (всего {count}):\n{rows}");
        this.unassignedTemplate = loadTemplate("/email-templates/unassigned-vehicles.txt",
                "Автобусы без маршрута на сегодня (всего {count}):\n{rows}");
    }

    @Scheduled(fixedRateString = "${app.fleet-presence-alerts.check-interval-minutes:10}", timeUnit = TimeUnit.MINUTES)
    public void scheduledCheck() {
        if (quietHours.active()) {
            return;
        }
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
        Optional<ShiftType> shiftOpt = ShiftType.operationalShiftAt(now);
        if (shiftOpt.isEmpty()) {
            return Mono.empty();
        }
        ShiftType shift = shiftOpt.get();
        LocalDate today = ShiftType.operationalDateAt(now);
        LocalDateTime nowUtc = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        LocalDateTime shiftStartUtc = LocalDateTime.ofInstant(shift.startInstantOn(today), ZoneOffset.UTC);

        Mono<List<FleetProblem>> assignedProblems = assignedVehicleIds(today, shift)
                .flatMap(vehicleId -> toProblem(vehicleId, today, shift, shiftStartUtc, nowUtc, now), CONCURRENCY)
                .collectList();
        Mono<List<RouteAssignment>> todayAssignments = assignmentsToday(today)
                .collectList().timeout(PER_ITEM_TIMEOUT);
        Mono<List<BusRoute>> activeRoutes = busRouteRepository.findActiveRoutes()
                .collectList().timeout(PER_ITEM_TIMEOUT);
        Mono<List<Vehicle>> activeVehicles = vehicleRepository.findActiveVehicles()
                .collectList().timeout(PER_ITEM_TIMEOUT);

        return Mono.zip(assignedProblems, todayAssignments, activeRoutes, activeVehicles)
                .flatMap(t -> {
                    List<FleetProblem> problems = t.getT1();
                    List<RouteAssignment> allAssignments = t.getT2();
                    List<BusRoute> routes = t.getT3();
                    List<Vehicle> vehicles = t.getT4();

                    List<RouteAssignment> currentShiftAssignments = allAssignments.stream()
                            .filter(a -> a.getShiftType() == shift || a.getShiftType() == ShiftType.FULL_DAY)
                            .toList();
                    Set<String> assignedTodayIds = new HashSet<>();
                    for (RouteAssignment a : allAssignments) {
                        assignedTodayIds.add(a.getVehicleId().getValue());
                    }
                    LocalDateTime graceCutoff = shiftStartUtc
                            .plusMinutes(properties.getStartupGraceMinutes());

                    List<EmptyRoute> emptyRoutes = EmptyRouteDetector.detect(routes, currentShiftAssignments,
                            vehicles, nowUtc, properties.getSilentThresholdMinutes(), graceCutoff);
                    List<UnassignedVehicle> unassigned = UnassignedVehicleDetector.detect(vehicles,
                            assignedTodayIds, nowUtc, properties.getSilentThresholdMinutes());

                    return dispatchIfNeeded(problems, emptyRoutes, unassigned, shift, now);
                });
    }

    private Flux<RouteAssignment> assignmentsToday(LocalDate today) {
        return Flux.concat(
                        assignmentRepository.findActiveByDateAndShift(today, ShiftType.FIRST),
                        assignmentRepository.findActiveByDateAndShift(today, ShiftType.SECOND),
                        assignmentRepository.findActiveByDateAndShift(today, ShiftType.FULL_DAY))
                .filter(RouteAssignment::isCurrentlyValid);
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
                                         LocalDateTime shiftStartUtc, LocalDateTime nowUtc, Instant now) {
        return vehicleRepository.findById(vehicleId)
                .timeout(PER_ITEM_TIMEOUT)
                .filter(v -> Boolean.TRUE.equals(v.getIsActive()))
                .flatMap(v -> {
                    Optional<OffRouteRecord> offRoute =
                            offRouteStateRegistry.find(vehicleId.getValue(), today, shift);
                    Optional<AssignedVehicleStatus> status =
                            classify(v.getLastPositionUpdate(), offRoute, nowUtc, shiftStartUtc, properties);
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

    private Mono<Void> dispatchIfNeeded(List<FleetProblem> problems, List<EmptyRoute> emptyRoutes,
                                        List<UnassignedVehicle> unassigned, ShiftType shift, Instant now) {
        if (problems.isEmpty() && emptyRoutes.isEmpty() && unassigned.isEmpty()) {
            return Mono.empty();
        }
        String hash = combinedHash(problems, emptyRoutes, unassigned);
        boolean changed = !hash.equals(lastSentHash);
        boolean cooldownPassed = lastSentAt == null
                || Duration.between(lastSentAt, now).toMinutes() >= properties.getMinResendCooldownMinutes();
        if (!changed || !cooldownPassed) {
            log.debug("[FLEET_PRESENCE] suppressed (changed={}, cooldownPassed={})", changed, cooldownPassed);
            return Mono.empty();
        }

        String subject = buildSubject(problems, emptyRoutes, unassigned, shift);
        String body = "<pre>" + renderBody(problems, emptyRoutes, unassigned, shift, now) + "</pre>";
        log.warn("[FLEET_PRESENCE] dispatching: assigned={}, emptyRoutes={}, unassigned={}, shift={}",
                problems.size(), emptyRoutes.size(), unassigned.size(), shift);

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

    private String buildSubject(List<FleetProblem> problems, List<EmptyRoute> emptyRoutes,
                                List<UnassignedVehicle> unassigned, ShiftType shift) {
        List<String> parts = new ArrayList<>();
        if (!problems.isEmpty()) {
            parts.add("не на линии: " + problems.size());
        }
        if (!emptyRoutes.isEmpty()) {
            parts.add("пустых маршрутов: " + emptyRoutes.size());
        }
        if (!unassigned.isEmpty()) {
            parts.add("без маршрута: " + unassigned.size());
        }
        return "[FLEET] " + String.join(" | ", parts) + " (смена " + shift.name() + ")";
    }

    private String combinedHash(List<FleetProblem> problems, List<EmptyRoute> emptyRoutes,
                                List<UnassignedVehicle> unassigned) {
        String a = problems.stream().map(p -> "A:" + p.vehicleId() + ":" + p.status().name())
                .sorted().collect(Collectors.joining("|"));
        String b = emptyRoutes.stream().map(r -> "B:" + r.routeNumber() + ":" + r.reason().name())
                .sorted().collect(Collectors.joining("|"));
        String c = unassigned.stream().map(u -> "C:" + u.licensePlate() + ":" + u.live())
                .sorted().collect(Collectors.joining("|"));
        return a + "#" + b + "#" + c;
    }

    private String renderBody(List<FleetProblem> problems, List<EmptyRoute> emptyRoutes,
                             List<UnassignedVehicle> unassigned, ShiftType shift, Instant now) {
        StringBuilder sb = new StringBuilder();
        if (!problems.isEmpty()) {
            sb.append(renderAssigned(problems, shift, now));
        }
        if (!emptyRoutes.isEmpty()) {
            appendSection(sb, renderEmptyRoutes(emptyRoutes, shift, now));
        }
        if (!unassigned.isEmpty()) {
            appendSection(sb, renderUnassigned(unassigned, shift, now));
        }
        return sb.toString();
    }

    private void appendSection(StringBuilder sb, String section) {
        if (sb.length() > 0) {
            sb.append("\n\n");
        }
        sb.append(section);
    }

    private String renderAssigned(List<FleetProblem> problems, ShiftType shift, Instant now) {
        List<String> rows = new ArrayList<>();
        for (FleetProblem p : problems) {
            rows.add(p.licensePlate() + " | " + p.routeNumber() + " | " + shift.name() + " | "
                    + p.status().name() + " | " + p.lastSignalAgo() + " | " + p.distanceMeters());
        }
        return emailTemplate
                .replace("{shift}", shift.name())
                .replace("{detectedAt}", formatInstant(now))
                .replace("{count}", String.valueOf(problems.size()))
                .replace("{rows}", String.join("\n", rows));
    }

    private String renderEmptyRoutes(List<EmptyRoute> routes, ShiftType shift, Instant now) {
        int cap = properties.getMaxRowsPerSection();
        List<String> rows = new ArrayList<>();
        int shown = Math.min(cap, routes.size());
        for (int i = 0; i < shown; i++) {
            EmptyRoute r = routes.get(i);
            rows.add(r.routeNumber() + " | " + reasonRu(r.reason()) + " | " + r.assignedCount());
        }
        if (routes.size() > cap) {
            rows.add("… и ещё " + (routes.size() - cap));
        }
        return emptyRoutesTemplate
                .replace("{shift}", shift.name())
                .replace("{detectedAt}", formatInstant(now))
                .replace("{count}", String.valueOf(routes.size()))
                .replace("{rows}", String.join("\n", rows));
    }

    private String renderUnassigned(List<UnassignedVehicle> list, ShiftType shift, Instant now) {
        int cap = properties.getMaxRowsPerSection();
        List<String> rows = new ArrayList<>();
        int shown = Math.min(cap, list.size());
        for (int i = 0; i < shown; i++) {
            UnassignedVehicle u = list.get(i);
            String gps = u.gpsRouteNumber() != null ? u.gpsRouteNumber() : "—";
            String status = u.live() ? "на линии" : "стоит";
            rows.add(u.licensePlate() + " | " + gps + " | " + status + " | " + u.lastSignalAgo());
        }
        if (list.size() > cap) {
            rows.add("… и ещё " + (list.size() - cap));
        }
        return unassignedTemplate
                .replace("{shift}", shift.name())
                .replace("{detectedAt}", formatInstant(now))
                .replace("{count}", String.valueOf(list.size()))
                .replace("{rows}", String.join("\n", rows));
    }

    private static String reasonRu(EmptyRouteReason reason) {
        return reason == EmptyRouteReason.ASSIGNED_BUT_SILENT ? "назначен, но молчат" : "не назначен";
    }

    private String loadTemplate(String path, String fallback) {
        try (var is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                return fallback;
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[FLEET_PRESENCE] template load failed {}: {}", path, e.getMessage());
            return fallback;
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
                                                    LocalDateTime nowUtc,
                                                    LocalDateTime shiftStartUtc,
                                                    FleetPresenceAlertProperties props) {
        if (offRoute.isPresent()) {
            return Optional.of(AssignedVehicleStatus.OFF_ROUTE);
        }

        if (lastPositionUpdate == null || lastPositionUpdate.isBefore(shiftStartUtc)) {
            long minutesSinceShiftStart = Duration.between(shiftStartUtc, nowUtc).toMinutes();
            if (minutesSinceShiftStart < props.getStartupGraceMinutes()) {
                return Optional.empty();
            }
            return Optional.of(AssignedVehicleStatus.NOT_STARTED);
        }

        LocalDateTime freshCutoff = nowUtc.minusMinutes(props.getSilentThresholdMinutes());
        if (lastPositionUpdate.isAfter(freshCutoff)) {
            return Optional.empty();
        }
        return Optional.of(AssignedVehicleStatus.WENT_SILENT);
    }

    public record FleetProblem(String vehicleId, String licensePlate, String routeNumber,
                               AssignedVehicleStatus status, String lastSignalAgo, String distanceMeters) {
    }
}
