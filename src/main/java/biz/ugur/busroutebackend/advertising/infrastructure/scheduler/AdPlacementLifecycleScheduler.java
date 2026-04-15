package biz.ugur.busroutebackend.advertising.infrastructure.scheduler;

import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Drives the time-based part of the {@link AdPlacement} FSM:
 *
 * <ul>
 *   <li><b>SCHEDULED → ACTIVE</b>: when the current time reaches {@code starts_at}.</li>
 *   <li><b>ACTIVE → EXPIRED</b>: when the current time passes {@code ends_at}.</li>
 * </ul>
 *
 * <p>The scheduler runs every minute — good enough granularity for daily/weekly ad windows.
 * Larger batches are pulled via pagination to avoid locking up on catch-up after downtime.
 */
@Component
@Slf4j
@ConditionalOnProperty(
        prefix = "advertising.scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class AdPlacementLifecycleScheduler {

    private static final int BATCH_SIZE = 100;

    private final AdPlacementRepository placementRepository;

    public AdPlacementLifecycleScheduler(AdPlacementRepository placementRepository) {
        this.placementRepository = placementRepository;
    }

    /** Runs every minute on the dot. */
    @Scheduled(cron = "0 * * * * *")
    public void tick() {
        LocalDateTime now = LocalDateTime.now();
        activateDue(now).subscribe();
        expireDue(now).subscribe();
    }

    private Mono<Void> activateDue(LocalDateTime moment) {
        return placementRepository.findDueToActivate(moment)
                .take(BATCH_SIZE)
                .flatMap(this::tryActivate)
                .then();
    }

    private Mono<Void> expireDue(LocalDateTime moment) {
        return placementRepository.findDueToExpire(moment)
                .take(BATCH_SIZE)
                .flatMap(this::tryExpire)
                .then();
    }

    private Mono<AdPlacement> tryActivate(AdPlacement placement) {
        try {
            AdPlacement next = placement.markAsActive();
            return placementRepository.save(next)
                    .doOnSuccess(p -> log.info(
                            "AdPlacement activated: id={} tariff={} business={}",
                            p.getId().getValue(), p.getTariffId().getValue(),
                            p.getBusinessId().getValue()));
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
                            p.getWindow() != null ? p.getWindow().getEndsAt() : null));
        } catch (Exception e) {
            log.warn("Cannot expire placement {} (status={}): {}",
                    placement.getId().getValue(), placement.getStatus(), e.getMessage());
            return Mono.empty();
        }
    }
}
