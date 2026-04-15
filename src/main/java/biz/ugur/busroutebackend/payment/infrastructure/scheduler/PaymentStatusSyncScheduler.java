package biz.ugur.busroutebackend.payment.infrastructure.scheduler;

import biz.ugur.busroutebackend.payment.application.usecase.admin.SyncPaymentStatusUseCase;
import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the payment provider for payments that are stuck in REGISTERED/PREAUTH longer than
 * a threshold — useful when the customer closes the browser before the return URL fires,
 * or when the provider's webhook never arrives.
 *
 * <p>Runs every 2 minutes. Fetches up to 50 stale orders per tick (older than 3 min and
 * younger than a sensible horizon to avoid thrashing on abandoned sessions).
 */
@Component
@Slf4j
@ConditionalOnProperty(
        prefix = "payment.scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PaymentStatusSyncScheduler {

    private static final long STALE_AFTER_SECONDS = 180;   // 3 min
    private static final int BATCH_SIZE = 50;

    private final PaymentRepository paymentRepository;
    private final SyncPaymentStatusUseCase syncPaymentStatusUseCase;

    public PaymentStatusSyncScheduler(PaymentRepository paymentRepository,
                                       SyncPaymentStatusUseCase syncPaymentStatusUseCase) {
        this.paymentRepository = paymentRepository;
        this.syncPaymentStatusUseCase = syncPaymentStatusUseCase;
    }

    @Scheduled(fixedDelay = 120_000, initialDelay = 60_000)
    public void tick() {
        paymentRepository.findPendingStale(STALE_AFTER_SECONDS, PageRequest.of(0, BATCH_SIZE))
                .flatMap(payment -> syncPaymentStatusUseCase.execute(payment.getId().getValue())
                        .doOnSuccess(result -> log.debug(
                                "Auto-sync payment {}: status={}",
                                result.id(), result.status()))
                        .onErrorResume(e -> {
                            log.warn("Auto-sync failed for payment {}: {}",
                                    payment.getId().getValue(), e.getMessage());
                            return reactor.core.publisher.Mono.empty();
                        }))
                .subscribe();
    }
}
