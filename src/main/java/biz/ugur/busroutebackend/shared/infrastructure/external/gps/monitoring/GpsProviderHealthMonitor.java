package biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring;

import biz.ugur.busroutebackend.shared.infrastructure.email.EmailNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "app.gps-alerts", name = "enabled", havingValue = "true")
@Slf4j
public class GpsProviderHealthMonitor {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EmailNotificationService emailService;
    private final GpsAlertProperties properties;
    private final Clock clock;
    private final Map<String, ProviderStatus> states = new ConcurrentHashMap<>();

    public GpsProviderHealthMonitor(EmailNotificationService emailService,
                                     GpsAlertProperties properties,
                                     Clock clock) {
        this.emailService = emailService;
        this.properties = properties;
        this.clock = clock;
    }

    public void recordFetch(String tenant, FetchOutcome outcome) {
        Instant now = clock.instant();
        states.compute(tenant, (k, oldOrNull) -> {
            ProviderStatus old = oldOrNull == null ? ProviderStatus.initial() : oldOrNull;
            ProviderStatus next = switch (outcome) {
                case FetchOutcome.Success s -> old.recordSuccess(now, s.deviceCount(), s.freshCount(),
                        s.latestFixTime(),
                        Duration.ofHours(properties.getDrop().getBaselineWindowHours()),
                        properties.getDrop().getMinBaseline());
                case FetchOutcome.Empty e -> old.recordEmpty(now);
                case FetchOutcome.HttpError h -> old.recordError(now, h.cause());
            };
            return evaluateTransition(tenant, next, now);
        });
    }

    public void recordError(String tenant, Throwable error) {
        recordFetch(tenant, new FetchOutcome.HttpError(error));
    }

    private ProviderStatus evaluateTransition(String tenant, ProviderStatus s, Instant now) {
        if (s.state() == ProviderStatus.State.DEGRADED) {
            return evaluateRecovery(tenant, s, now);
        }
        if (s.consecutiveFailures() >= properties.getHttpError().getConsecutiveFailures()) {
            ProviderStatus degraded = s.markDegraded(AlertKind.HTTP_ERROR, now);
            dispatch(tenant, AlertKind.HTTP_ERROR, degraded, now);
            return degraded;
        }
        if (s.consecutiveEmpty() >= properties.getEmpty().getConsecutiveEmpty()) {
            ProviderStatus degraded = s.markDegraded(AlertKind.EMPTY, now);
            dispatch(tenant, AlertKind.EMPTY, degraded, now);
            return degraded;
        }
        if (s.lastDeviceCount() > 0
                && s.baselineDeviceCount() >= properties.getDrop().getMinBaseline()) {
            int percent = (int) Math.round(100.0 * s.lastDeviceCount() / s.baselineDeviceCount());
            if (percent < properties.getDrop().getThresholdPercent()) {
                ProviderStatus degraded = s.markDegraded(AlertKind.DROP, now);
                dispatch(tenant, AlertKind.DROP, degraded, now);
                return degraded;
            }
        }
        if (s.lastDeviceCount() > 0) {
            int staleCount = s.lastDeviceCount() - s.lastFreshCount();
            int stalePercent = (int) Math.round(100.0 * staleCount / s.lastDeviceCount());
            if (stalePercent >= properties.getStale().getDegradedPercent()) {
                ProviderStatus degraded = s.markDegraded(AlertKind.STALE, now);
                dispatch(tenant, AlertKind.STALE, degraded, now);
                return degraded;
            }
        }
        return s;
    }

    private void dispatch(String tenant, AlertKind kind, ProviderStatus s, Instant now) {
        String subject = "[GPS ALERT] " + tenant + " — " + kindRu(kind) + " (since " + now.toString().substring(11, 16) + ")";
        Map<String, String> vars = buildAlertVars(tenant, kind, s, now);
        String body = "<pre>" + renderTemplate(kind, vars) + "</pre>";
        log.warn("[GPS_ALERT] {} transition OK->DEGRADED reason={} since={}", tenant, kind, now);
        emailService.sendGpsAlert(properties.recipientList(), tenant, kind, subject, body)
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(err -> {
                    log.error("[GPS_ALERT] dispatch failed for {} {}: {}", tenant, kind, err.toString());
                    return reactor.core.publisher.Mono.empty();
                })
                .subscribe();
    }

    private Map<String, String> buildAlertVars(String tenant, AlertKind kind, ProviderStatus s, Instant now) {
        Map<String, String> vars = new HashMap<>();
        vars.put("tenant", tenant);
        vars.put("detectedAt", formatInstant(now));

        switch (kind) {
            case HTTP_ERROR -> {
                vars.put("lastSuccess", s.lastSuccessfulFetchAt() != null ? formatInstant(s.lastSuccessfulFetchAt()) : "-");
                vars.put("lastSuccessAgo", s.lastSuccessfulFetchAt() != null
                        ? humanDuration(Duration.between(s.lastSuccessfulFetchAt(), now)) : "-");
                vars.put("consecutiveFailures", String.valueOf(s.consecutiveFailures()));
                vars.put("lastErrorClass", s.lastError() != null ? s.lastError().getClass().getSimpleName() : "-");
                vars.put("lastErrorMessage", s.lastError() != null && s.lastError().getMessage() != null
                        ? s.lastError().getMessage() : "-");
            }
            case EMPTY -> {
                vars.put("lastSuccess", s.lastSuccessfulFetchAt() != null ? formatInstant(s.lastSuccessfulFetchAt()) : "-");
                vars.put("lastDeviceCount", String.valueOf(s.lastDeviceCount()));
                vars.put("consecutiveEmpty", String.valueOf(s.consecutiveEmpty()));
            }
            case DROP -> {
                int currentPercent = s.baselineDeviceCount() > 0
                        ? (int) Math.round(100.0 * s.lastDeviceCount() / s.baselineDeviceCount()) : 0;
                vars.put("currentDeviceCount", String.valueOf(s.lastDeviceCount()));
                vars.put("baselineDeviceCount", String.valueOf(s.baselineDeviceCount()));
                vars.put("currentPercent", String.valueOf(currentPercent));
                vars.put("thresholdPercent", String.valueOf(properties.getDrop().getThresholdPercent()));
            }
            case STALE -> {
                int staleCount = s.lastDeviceCount() - s.lastFreshCount();
                int stalePercent = s.lastDeviceCount() > 0
                        ? (int) Math.round(100.0 * staleCount / s.lastDeviceCount()) : 0;
                vars.put("totalCount", String.valueOf(s.lastDeviceCount()));
                vars.put("maxFixAgeMin", String.valueOf(properties.getStale().getMaxFixAgeMinutes()));
                vars.put("staleCount", String.valueOf(staleCount));
                vars.put("stalePercent", String.valueOf(stalePercent));
                vars.put("latestFixTime", s.latestFixTime() != null ? formatInstant(s.latestFixTime()) : "-");
                vars.put("latestFixTimeAgo", s.latestFixTime() != null
                        ? humanDuration(Duration.between(s.latestFixTime(), now)) : "-");
            }
            default -> {
            }
        }
        return vars;
    }

    private String kindRu(AlertKind kind) {
        return switch (kind) {
            case HTTP_ERROR -> "недоступен (HTTP/timeout)";
            case EMPTY -> "пустой ответ";
            case DROP -> "резкое падение количества автобусов";
            case STALE -> "данные устарели";
            case RECOVERY -> "восстановлен";
            case VEHICLE_OFF_ROUTE -> "автобус уехал с маршрута";
            case ASSIGNED_NOT_ON_LINE -> "назначенные автобусы не на линии";
        };
    }

    private ProviderStatus evaluateRecovery(String tenant, ProviderStatus s, Instant now) {
        boolean cleanFetch = s.consecutiveFailures() == 0
                && s.consecutiveEmpty() == 0
                && s.lastDeviceCount() > 0
                && isFreshAndNotDropped(s);
        if (!cleanFetch) {
            return s;
        }
        ProviderStatus withClear = s.incrementClear();
        if (withClear.consecutiveClear() >= properties.getRecovery().getClearFetches()) {
            Duration downtime = Duration.between(s.degradedSince(), now);
            AlertKind previous = s.degradedReason();
            ProviderStatus recovered = withClear.markRecovered();
            dispatchRecovery(tenant, previous, s.degradedSince(), now, downtime, withClear);
            return recovered;
        }
        return withClear;
    }

    private boolean isFreshAndNotDropped(ProviderStatus s) {
        if (s.baselineDeviceCount() >= properties.getDrop().getMinBaseline()) {
            int percent = (int) Math.round(100.0 * s.lastDeviceCount() / s.baselineDeviceCount());
            if (percent < properties.getDrop().getThresholdPercent()) return false;
        }
        int staleCount = s.lastDeviceCount() - s.lastFreshCount();
        int stalePercent = (int) Math.round(100.0 * staleCount / s.lastDeviceCount());
        return stalePercent < properties.getStale().getDegradedPercent();
    }

    private void dispatchRecovery(String tenant, AlertKind previous, Instant since,
                                   Instant recoveredAt, Duration downtime, ProviderStatus s) {
        String subject = "[GPS RECOVERY] " + tenant + " — восстановлен (downtime "
                + humanDuration(downtime) + ")";
        Map<String, String> vars = new HashMap<>();
        vars.put("tenant", tenant);
        vars.put("recoveredAt", formatInstant(recoveredAt));
        vars.put("degradedSince", since != null ? formatInstant(since) : "-");
        vars.put("downtimeHuman", humanDuration(downtime));
        vars.put("previousReason", previous != null ? previous.name() : "-");
        vars.put("currentDeviceCount", String.valueOf(s.lastDeviceCount()));
        vars.put("latestFixTime", s.latestFixTime() != null ? formatInstant(s.latestFixTime()) : "-");
        String body = "<pre>" + renderTemplate(AlertKind.RECOVERY, vars) + "</pre>";
        log.info("[GPS_ALERT] {} transition DEGRADED->OK previous={} downtime={}",
                tenant, previous, humanDuration(downtime));
        emailService.sendGpsAlert(properties.recipientList(), tenant, AlertKind.RECOVERY, subject, body)
                .onErrorResume(err -> {
                    log.error("[GPS_ALERT] recovery dispatch failed for {}: {}", tenant, err.toString());
                    return reactor.core.publisher.Mono.empty();
                })
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .subscribe();
    }

    private String renderTemplate(AlertKind kind, Map<String, String> vars) {
        String resourcePath = "/email-templates/gps-alert-" + kind.name().toLowerCase() + ".txt";
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.error("[GPS_ALERT] template not found: {}", resourcePath);
                return fallbackBody(kind, vars);
            }
            String template = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                template = template.replace("{" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
            }
            return template;
        } catch (IOException e) {
            log.error("[GPS_ALERT] failed to load template {}: {}", resourcePath, e.getMessage());
            return fallbackBody(kind, vars);
        }
    }

    private String fallbackBody(AlertKind kind, Map<String, String> vars) {
        return "GPS provider " + vars.getOrDefault("tenant", "-") + " — " + kind;
    }

    private String formatInstant(Instant t) {
        return OffsetDateTime.ofInstant(t, ZoneOffset.UTC).format(TIMESTAMP_FMT);
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
}
