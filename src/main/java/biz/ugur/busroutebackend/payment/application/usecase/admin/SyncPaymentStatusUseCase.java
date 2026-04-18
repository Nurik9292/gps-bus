package biz.ugur.busroutebackend.payment.application.usecase.admin;

import biz.ugur.busroutebackend.payment.application.dto.PaymentResponse;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;
import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentNotFoundException;
import biz.ugur.busroutebackend.payment.domain.model.Payment;
import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
import biz.ugur.busroutebackend.payment.domain.services.PaymentOrchestrator;
import biz.ugur.busroutebackend.payment.domain.services.PaymentProviderGateway;
import biz.ugur.busroutebackend.payment.domain.valueobjects.PaymentId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;


@Service
@Slf4j
public class SyncPaymentStatusUseCase extends BaseUseCase<String, PaymentResponse> {

    private final PaymentRepository paymentRepository;
    private final PaymentOrchestrator paymentOrchestrator;

    public SyncPaymentStatusUseCase(PaymentRepository paymentRepository,
                                     PaymentOrchestrator paymentOrchestrator,
                                     CorrelationContextService correlationService,
                                     EventBus eventBus) {
        super(correlationService, eventBus);
        this.paymentRepository = paymentRepository;
        this.paymentOrchestrator = paymentOrchestrator;
    }

    @Override
    protected Mono<PaymentResponse> process(String paymentId) {
        return paymentRepository.findById(PaymentId.of(paymentId))
                .switchIfEmpty(Mono.error(new PaymentNotFoundException(paymentId)))
                .flatMap(payment -> payment.isTerminal()
                        ? Mono.just(payment)         
                        : reconcile(payment))
                .flatMap(paymentRepository::save)
                .transform(this::persistAndPublish)
                .map(PaymentResponse::fromDomain);
    }

    @Override
    protected String getBoundContext() { return "payment.admin"; }

    private Mono<Payment> reconcile(Payment payment) {
        if (payment.getProviderOrderId() == null) {
            log.debug("Payment {} has no providerOrderId yet, skipping status sync",
                    payment.getId().getValue());
            return Mono.just(payment);
        }
        return paymentOrchestrator.getStatus(payment)
                .map(result -> applyStatus(payment, result))
                .onErrorResume(err -> {
                    log.warn("Status sync failed for {}: {}",
                            payment.getId().getValue(), err.getMessage());
                    return Mono.just(payment);
                });
    }

    private static Payment applyStatus(Payment payment, PaymentProviderGateway.StatusResult r) {
        PaymentStatus mapped = PaymentStatus.fromSvEpgOrderStatus(r.orderStatusCode());
        if (mapped == payment.getStatus()) {
            return payment;
        }
        return switch (mapped) {
            case COMPLETED ->
                    payment.getStatus() == PaymentStatus.COMPLETED
                            ? payment
                            : payment.markCompleted(r.cardPanMasked(), r.cardExpiration(), r.cardholderName());
            case DECLINED  ->
                    payment.getStatus().isTerminal() ? payment
                            : payment.markDeclined(r.errorCode(), r.errorMessage());
            case REVERSED  ->
                    payment.getStatus().isTerminal() ? payment : payment.markReversed();
            case REFUNDED  ->
                    payment.getStatus() == PaymentStatus.COMPLETED ? payment.markRefunded() : payment;
            default        -> payment;
        };
    }
}
