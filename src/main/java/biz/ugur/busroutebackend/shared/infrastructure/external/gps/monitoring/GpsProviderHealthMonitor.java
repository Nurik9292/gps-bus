package biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring;

import biz.ugur.busroutebackend.shared.infrastructure.email.EmailNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.scheduler.Schedulers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(prefix = "app.gps-alerts", name = "enabled", havingValue = "true")
@Slf4j
public class GpsProviderHealthMonitor {

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
        if (s.state() != ProviderStatus.State.OK) {
            return s;
        }
        if (s.consecutiveFailures() >= properties.getHttpError().getConsecutiveFailures()) {
            ProviderStatus degraded = s.markDegraded(AlertKind.HTTP_ERROR, now);
            dispatch(tenant, AlertKind.HTTP_ERROR,
                    "Подряд ошибок: " + s.consecutiveFailures()
                            + ". Последняя: " + (s.lastError() == null ? "n/a" : s.lastError().toString()),
                    now, null);
            return degraded;
        }
        if (s.consecutiveEmpty() >= properties.getEmpty().getConsecutiveEmpty()) {
            ProviderStatus degraded = s.markDegraded(AlertKind.EMPTY, now);
            dispatch(tenant, AlertKind.EMPTY,
                    "Подряд пустых ответов: " + s.consecutiveEmpty(),
                    now, null);
            return degraded;
        }
        return s;
    }

    private void dispatch(String tenant, AlertKind kind, String details, Instant since, Object unused) {
        String subject = "[GPS ALERT] " + tenant + " — " + kindRu(kind) + " (since " + since.toString().substring(11, 16) + ")";
        String body = "GPS provider " + tenant + " — " + kind + ".\nDetails: " + details + "\n";
        log.warn("[GPS_ALERT] {} transition OK->DEGRADED reason={} since={}", tenant, kind, since);
        emailService.sendGpsAlert(properties.recipientList(), tenant, kind, subject, body)
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(err -> {
                    log.error("[GPS_ALERT] dispatch failed for {} {}: {}", tenant, kind, err.toString());
                    return reactor.core.publisher.Mono.empty();
                })
                .subscribe();
    }

    private String kindRu(AlertKind kind) {
        return switch (kind) {
            case HTTP_ERROR -> "недоступен (HTTP/timeout)";
            case EMPTY -> "пустой ответ";
            case DROP -> "резкое падение количества автобусов";
            case STALE -> "данные устарели";
            case RECOVERY -> "восстановлен";
        };
    }
}
