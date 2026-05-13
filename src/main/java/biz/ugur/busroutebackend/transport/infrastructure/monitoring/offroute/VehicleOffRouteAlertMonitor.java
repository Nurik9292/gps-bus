package biz.ugur.busroutebackend.transport.infrastructure.monitoring.offroute;

import biz.ugur.busroutebackend.shared.infrastructure.email.EmailNotificationService;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring.AlertKind;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePredictionState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "app.off-route-alerts", name = "enabled", havingValue = "true")
@Slf4j
public class VehicleOffRouteAlertMonitor {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EmailNotificationService emailService;
    private final OffRouteAlertProperties properties;
    private final Clock clock;
    private final RouteAssignmentRepository routeAssignmentRepository;
    private final VehicleRepository vehicleRepository;
    private final BusRouteRepository busRouteRepository;

    private final Map<OffRouteAlertKey, Instant> alerted = new ConcurrentHashMap<>();

    public VehicleOffRouteAlertMonitor(EmailNotificationService emailService,
                                       OffRouteAlertProperties properties,
                                       Clock clock,
                                       RouteAssignmentRepository routeAssignmentRepository,
                                       VehicleRepository vehicleRepository,
                                       BusRouteRepository busRouteRepository) {
        this.emailService = emailService;
        this.properties = properties;
        this.clock = clock;
        this.routeAssignmentRepository = routeAssignmentRepository;
        this.vehicleRepository = vehicleRepository;
        this.busRouteRepository = busRouteRepository;
    }

    public void onWentOffRoute(String vehicleId, VehiclePredictionState state,
                               double latitude, double longitude, double distanceFromRouteMeters) {
        Instant now = clock.instant();
        Optional<ShiftType> shiftOpt = currentShift(now);
        if (shiftOpt.isEmpty()) {
            return;
        }
        ShiftType shift = shiftOpt.get();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        OffRouteAlertKey key = new OffRouteAlertKey(vehicleId, today, shift);

        if (alerted.putIfAbsent(key, now) != null) {
            return;
        }

        routeAssignmentRepository
                .findActiveByVehicleAndDateAndShift(VehicleId.of(vehicleId), today, shift)
                .switchIfEmpty(Mono.defer(() -> routeAssignmentRepository
                        .findActiveByVehicleAndDateAndShift(VehicleId.of(vehicleId), today, ShiftType.FULL_DAY)))
                .filter(a -> a.shouldBeActiveAt(LocalTime.ofInstant(now, ZoneOffset.UTC)))
                .filter(a -> minutesUntilShiftEnd(shift, now) >= properties.getEndOfShiftBufferMinutes())
                .filter(a -> hasBeenOnRouteLongEnough(state, now))
                .switchIfEmpty(Mono.fromRunnable(() -> alerted.remove(key)).then(Mono.empty()))
                .flatMap(a -> dispatch(vehicleId, state, latitude, longitude,
                        distanceFromRouteMeters, shift, now, a))
                .onErrorResume(err -> {
                    alerted.remove(key);
                    log.error("[OFF_ROUTE] dispatch failed for {}: {}", vehicleId, err.toString());
                    return Mono.empty();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    private Mono<Void> dispatch(String vehicleId, VehiclePredictionState state,
                                double latitude, double longitude, double distanceFromRouteMeters,
                                ShiftType shift, Instant now, RouteAssignment assignment) {
        return Mono.zip(
                vehicleRepository.findById(assignment.getVehicleId())
                        .map(v -> v.getLicensePlate())
                        .defaultIfEmpty(state.getLicensePlate() != null ? state.getLicensePlate() : vehicleId),
                busRouteRepository.findById(assignment.getRouteId())
                        .map(r -> r.getRouteNumber())
                        .defaultIfEmpty(state.getRouteNumber() != null ? state.getRouteNumber() : assignment.getRouteId().toString())
        ).flatMap(tuple -> {
            String licensePlate = tuple.getT1();
            String routeNumber = tuple.getT2();
            Map<String, String> vars = buildVars(vehicleId, licensePlate, routeNumber,
                    state, latitude, longitude, distanceFromRouteMeters, shift, now);
            String subject = "[OFF-ROUTE] vehicle " + licensePlate + " (route " + routeNumber + ") — "
                    + vars.get("minutesUntilShiftEnd") + "min until shift end";
            String body = "<pre>" + renderTemplate(vars) + "</pre>";
            log.warn("[OFF_ROUTE] {} ({}) off-route on route {} at distance {}m, alerting",
                    licensePlate, vehicleId, routeNumber, distanceFromRouteMeters);
            return emailService.sendGpsAlert(properties.recipientList(),
                    vehicleId, AlertKind.VEHICLE_OFF_ROUTE, subject, body);
        });
    }

    private Map<String, String> buildVars(String vehicleId, String licensePlate, String routeNumber,
                                          VehiclePredictionState state,
                                          double latitude, double longitude, double distanceFromRouteMeters,
                                          ShiftType shift, Instant now) {
        Map<String, String> vars = new HashMap<>();
        vars.put("vehicleId", vehicleId);
        vars.put("licensePlate", licensePlate);
        vars.put("routeNumber", routeNumber);
        vars.put("detectedAt", formatInstant(now));
        vars.put("shiftName", shift.name());
        vars.put("shiftStart", shift.getStartTime().toString());
        vars.put("shiftEnd", shift.getEndTime().toString());
        vars.put("minutesUntilShiftEnd", String.valueOf(minutesUntilShiftEnd(shift, now)));
        vars.put("latitude", String.valueOf(latitude));
        vars.put("longitude", String.valueOf(longitude));
        vars.put("distanceFromRouteMeters",
                Double.isNaN(distanceFromRouteMeters) ? "-"
                        : String.valueOf((int) Math.round(distanceFromRouteMeters)));
        vars.put("firstOnRouteAt", state.getFirstOnRouteAtCurrentShift() != null
                ? formatInstant(state.getFirstOnRouteAtCurrentShift()) : "-");
        vars.put("onRouteDurationHuman", state.getFirstOnRouteAtCurrentShift() != null
                ? humanDuration(Duration.between(state.getFirstOnRouteAtCurrentShift(), now)) : "-");
        vars.put("lastOnRouteAt", state.getLastOnRouteAt() != null
                ? formatInstant(state.getLastOnRouteAt()) : "-");
        vars.put("lastOnRouteAgo", state.getLastOnRouteAt() != null
                ? humanDuration(Duration.between(state.getLastOnRouteAt(), now)) : "-");
        vars.put("thresholdMeters", "200");
        return vars;
    }

    private Optional<ShiftType> currentShift(Instant now) {
        LocalTime t = LocalTime.ofInstant(now, ZoneOffset.UTC);
        return Arrays.stream(ShiftType.values())
                .filter(s -> s.isActiveAt(t))
                .findFirst();
    }

    private long minutesUntilShiftEnd(ShiftType shift, Instant now) {
        LocalTime t = LocalTime.ofInstant(now, ZoneOffset.UTC);
        return Duration.between(t, shift.getEndTime()).toMinutes();
    }

    private boolean hasBeenOnRouteLongEnough(VehiclePredictionState state, Instant now) {
        if (state.getFirstOnRouteAtCurrentShift() == null) {
            return false;
        }
        long onRouteSec = Duration.between(state.getFirstOnRouteAtCurrentShift(), now).toSeconds();
        return onRouteSec >= properties.getMinOnRouteSeconds();
    }

    private String renderTemplate(Map<String, String> vars) {
        String resourcePath = "/email-templates/vehicle-off-route.txt";
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.error("[OFF_ROUTE] template not found: {}", resourcePath);
                return fallbackBody(vars);
            }
            String template = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                template = template.replace("{" + entry.getKey() + "}",
                        entry.getValue() != null ? entry.getValue() : "");
            }
            return template;
        } catch (IOException e) {
            log.error("[OFF_ROUTE] failed to load template: {}", e.getMessage());
            return fallbackBody(vars);
        }
    }

    private String fallbackBody(Map<String, String> vars) {
        return "Vehicle " + vars.getOrDefault("licensePlate", "-")
                + " went off route on route " + vars.getOrDefault("routeNumber", "-");
    }

    private String formatInstant(Instant t) {
        return OffsetDateTime.ofInstant(t, ZoneOffset.UTC).format(TS_FMT);
    }

    private String humanDuration(Duration d) {
        long h = d.toHours();
        long m = d.toMinutesPart();
        long sec = d.toSecondsPart();
        StringBuilder sb = new StringBuilder();
        if (h > 0) sb.append(h).append("ч ");
        if (m > 0) sb.append(m).append("м ");
        sb.append(sec).append("с");
        return sb.toString();
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "UTC")
    public void cleanupOldEntries() {
        LocalDate cutoff = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).minusDays(1);
        int removed = 0;
        var it = alerted.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (entry.getKey().date().isBefore(cutoff)) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.info("[OFF_ROUTE] cleaned {} old alert records (cutoff {})", removed, cutoff);
        }
    }
}
