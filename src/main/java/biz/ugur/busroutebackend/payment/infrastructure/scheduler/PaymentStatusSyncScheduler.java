package biz.ugur.busroutebackend.payment.infrastructure.scheduler;

import biz.ugur.busroutebackend.payment.application.usecase.admin.SyncPaymentStatusUseCase;
import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
import biz.ugur.busroutebackend.payment.infrastructure.config.PaymentSchedulerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
@Slf4j
@ConditionalOnProperty(
        prefix = "payment.scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PaymentStatusSyncScheduler {

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
