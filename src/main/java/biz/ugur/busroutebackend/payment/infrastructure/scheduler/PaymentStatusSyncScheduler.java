package biz.ugur.busroutebackend.payment.infrastructure.scheduler;

import biz.ugur.busroutebackend.payment.application.usecase.admin.SyncPaymentStatusUseCase;
import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
import biz.ugur.busroutebackend.payment.infrastructure.config.PaymentSchedulerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;


@Component
@Slf4j
@ConditionalOnProperty(
        prefix = "payment.scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PaymentStatusSyncScheduler {

    private static final int CONCURRENCY = 4;
    private static final Duration TICK_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration PER_ITEM_TIMEOUT = Duration.ofSeconds(10);

    private final PaymentRepository paymentRepository;
    private final SyncPaymentStatusUseCase syncPaymentStatusUseCase;
    private final PaymentSchedulerProperties properties;

    public PaymentStatusSyncScheduler(PaymentRepository paymentRepository,
                                       SyncPaymentStatusUseCase syncPaymentStatusUseCase,
                                       PaymentSchedulerProperties properties) {
        this.paymentRepository = paymentRepository;
        this.syncPaymentStatusUseCase = syncPaymentStatusUseCase;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${payment.scheduler.fixed-delay-ms:120000}",
            initialDelayString = "${payment.scheduler.initial-delay-ms:60000}")
    public void tick() {
        paymentRepository.findPendingStale(properties.getStaleAfterSeconds(),
                        PageRequest.of(0, properties.getBatchSize()))
                .flatMap(payment -> syncPaymentStatusUseCase.execute(payment.getId().getValue())
                        .timeout(PER_ITEM_TIMEOUT)
                        .doOnSuccess(result -> log.debug(
                                "Auto-sync payment {}: status={}",
                                result.id(), result.status()))
                        .onErrorResume(e -> {
                            log.warn("Auto-sync failed for payment {}: {}",
                                    payment.getId().getValue(), e.getMessage());
                            return Mono.empty();
                        }), CONCURRENCY)
                .then()
                .timeout(TICK_TIMEOUT)
                .doOnError(err -> log.warn("[SCHEDULER] PaymentStatusSync tick failed: {}", err.toString()))
                .onErrorResume(err -> Mono.empty())
                .subscribe();
    }
}
