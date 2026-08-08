package biz.ugur.busroutebackend.transport.infrastructure.monitoring.routeswap;

import biz.ugur.busroutebackend.shared.infrastructure.email.AlertQuietHours;
import biz.ugur.busroutebackend.shared.infrastructure.email.EmailNotificationService;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring.AlertKind;
import biz.ugur.busroutebackend.transport.application.services.ExternalApiService;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.repository.RouteSwapAuditRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ProviderAssignmentCrossCheck {

    private static final Duration TASK_TIMEOUT = Duration.ofMinutes(2);
    private static final int VERDICT_CONCURRENCY = 4;

    private final RouteSwapProperties properties;
    private final ExternalApiService externalApiService;
    private final VehicleRepository vehicleRepository;
    private final RouteSwapAuditRepository auditRepository;
    private final EmailNotificationService emailService;
    private final AlertQuietHours quietHours;
    private final Clock clock;
    private final AtomicBoolean inProgress = new AtomicBoolean(false);
    private final ConcurrentLinkedQueue<String> pendingDigestLines = new ConcurrentLinkedQueue<>();

    public ProviderAssignmentCrossCheck(RouteSwapProperties properties,
                                        ExternalApiService externalApiService,
                                        VehicleRepository vehicleRepository,
                                        RouteSwapAuditRepository auditRepository,
                                        EmailNotificationService emailService,
                                        AlertQuietHours quietHours,
                                        Clock clock) {
        this.properties = properties;
        this.externalApiService = externalApiService;
        this.vehicleRepository = vehicleRepository;
        this.auditRepository = auditRepository;
        this.emailService = emailService;
        this.quietHours = quietHours;
        this.clock = clock;
    }

    @Scheduled(cron = "${business.route-swap-detector.provider-check-cron:0 30 7,15 * * *}",
            zone = "Asia/Ashgabat")
    public void scheduledCheck() {
        if (!properties.isProviderCheckEnabled() || properties.getMode() == RouteSwapProperties.Mode.OFF) {
            return;
        }
        if (!inProgress.compareAndSet(false, true)) {
            log.warn("[ROUTE_SWAP] provider cross-check already in progress, skipping");
            return;
        }
        checkNow()
                .timeout(TASK_TIMEOUT)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(error -> log.error("[ROUTE_SWAP] provider cross-check failed", error))
                .onErrorComplete()
                .doFinally(signal -> inProgress.set(false))
                .subscribe();
    }

    public Mono<Void> checkNow() {
        return Mono.zip(
                        externalApiService.fetchAllBusInfo(),
                        vehicleRepository.findActiveVehicles().collectList())
                .flatMap(tuple -> {
                    if (tuple.getT1().isEmpty()) {
                        log.info("[ROUTE_SWAP] provider registry empty or disabled, cross-check skipped");
                        return Mono.empty();
                    }
                    List<ProviderMismatchDetector.Mismatch> mismatches =
                            ProviderMismatchDetector.detect(tuple.getT1(), tuple.getT2());
                    log.info("[ROUTE_SWAP] provider cross-check: registry={} vehicles={} mismatches={}",
                            tuple.getT1().size(), tuple.getT2().size(), mismatches.size());
                    return recordAndNotify(mismatches);
                });
    }

    private Mono<Void> recordAndNotify(List<ProviderMismatchDetector.Mismatch> mismatches) {
        Instant now = clock.instant();
        LocalDate operationalDate = ShiftType.operationalDateAt(now);
        String shift = ShiftType.operationalShiftAt(now).map(Enum::name).orElse("OUTSIDE");
        return Flux.fromIterable(mismatches)
                .flatMap(mismatch -> {
                    String detail = String.format("БД=%s, провайдер=r%s",
                            mismatch.dbRouteNumber() == null ? "нет назначения" : "r" + mismatch.dbRouteNumber(),
                            mismatch.providerRouteNumber());
                    return auditRepository.tryRecordVerdict(mismatch.licensePlate(), mismatch.vehicleId(),
                                    mismatch.dbRouteNumber(), "PROVIDER_MISMATCH", detail,
                                    operationalDate, shift)
                            .filter(Boolean::booleanValue)
                            .map(inserted -> String.format("%s | %s", mismatch.licensePlate(), detail))
                            .doOnNext(line -> log.warn("[ROUTE_SWAP] PROVIDER_MISMATCH {}", line))
                            .onErrorResume(error -> {
                                log.warn("[ROUTE_SWAP] provider-mismatch persist failed for {}: {}",
                                        mismatch.licensePlate(), error.getMessage());
                                return Mono.empty();
                            });
                }, VERDICT_CONCURRENCY)
                .collectList()
                .flatMap(this::sendDigest);
    }

    private Mono<Void> sendDigest(List<String> freshLines) {
        pendingDigestLines.addAll(freshLines);
        if (pendingDigestLines.isEmpty()) {
            return Mono.empty();
        }
        if (quietHours.active()) {
            log.info("[ROUTE_SWAP] provider digest deferred by quiet hours, buffered lines={}",
                    pendingDigestLines.size());
            return Mono.empty();
        }
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = pendingDigestLines.poll()) != null) {
            lines.add(line);
        }
        String subject = "[ROUTE_SWAP] расхождения с реестром провайдера: " + lines.size();
        String body = "<pre>" + String.join("\n", lines) + "</pre>";
        return emailService.sendGpsAlert(properties.recipientList(), "route-swap-provider",
                        AlertKind.ROUTE_SWAP, subject, body)
                .timeout(Duration.ofSeconds(30))
                .doOnError(error -> log.error("[ROUTE_SWAP] provider digest send failed", error))
                .onErrorResume(error -> Mono.empty());
    }
}
