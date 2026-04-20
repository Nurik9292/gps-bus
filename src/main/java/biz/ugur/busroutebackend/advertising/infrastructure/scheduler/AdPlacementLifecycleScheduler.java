package biz.ugur.busroutebackend.advertising.infrastructure.scheduler;

import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@Slf4j
@ConditionalOnProperty(
        prefix = "advertising.scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AdPlacementLifecycleScheduler {

    private static final int BATCH_SIZE = 100;
    private static final int CONCURRENCY = 8;
    private static final Duration TICK_TIMEOUT = Duration.ofMinutes(1);

    private final AdPlacementRepository placementRepository;

    public AdPlacementLifecycleScheduler(AdPlacementRepository placementRepository) {
        this.placementRepository = placementRepository;
    }

    @Scheduled(cron = "0 * * * * *")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        activateDue(now)
                .timeout(TICK_TIMEOUT)
                .doOnError(err -> log.warn("[SCHEDULER] AdPlacement activate tick failed: {}", err.toString()))
                .onErrorResume(err -> Mono.empty())
                .subscribe();
        expireDue(now)
                .timeout(TICK_TIMEOUT)
                .doOnError(err -> log.warn("[SCHEDULER] AdPlacement expire tick failed: {}", err.toString()))
                .onErrorResume(err -> Mono.empty())
                .subscribe();
    }

    private Mono<Void> activateDue(LocalDateTime moment) {
        return placementRepository.findDueToActivate(moment)
                .take(BATCH_SIZE)
                .flatMap(this::tryActivate, CONCURRENCY)
                .then();
    }

    private Mono<Void> expireDue(LocalDateTime moment) {
        return placementRepository.findDueToExpire(moment)
                .take(BATCH_SIZE)
                .flatMap(this::tryExpire, CONCURRENCY)
                .then();
    }

    private Mono<AdPlacement> tryActivate(AdPlacement placement) {
        try {
            AdPlacement next = placement.markAsActive();
            return placementRepository.save(next)
                    .doOnSuccess(p -> log.info(
                            "AdPlacement activated: id={} tariff={} business={}",
                            p.getId().getValue(), p.getTariffId().getValue(),
                            p.getBusinessId().getValue()))
                    .doOnError(err -> log.warn("AdPlacement activate save failed: id={} err={}",
                            placement.getId().getValue(), err.toString()))
                    .onErrorResume(err -> Mono.empty());
        } catch (Exception e) {
            log.warn("Cannot activate placement {} (status={}): {}",
                    placement.getId().getValue(), placement.getStatus(), e.getMessage());
            return Mono.empty();
        }
    }

    private Mono<AdPlacement> tryExpire(AdPlacement placement) {
        try {
            AdPlacement next = placement.markAsExpired();
            return placementRepository.save(next)
                    .doOnSuccess(p -> log.info(
                            "AdPlacement expired: id={} ended_at={}",
                            p.getId().getValue(),
                            p.getWindow() != null ? p.getWindow().getEndsAt() : null))
                    .doOnError(err -> log.warn("AdPlacement expire save failed: id={} err={}",
                            placement.getId().getValue(), err.toString()))
                    .onErrorResume(err -> Mono.empty());
        } catch (Exception e) {
            log.warn("Cannot expire placement {} (status={}): {}",
                    placement.getId().getValue(), placement.getStatus(), e.getMessage());
            return Mono.empty();
        }
    }
}
