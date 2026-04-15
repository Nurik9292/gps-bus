package biz.ugur.busroutebackend.payment.application.usecase.admin;

import biz.ugur.busroutebackend.payment.application.dto.PaymentResponse;
import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentNotFoundException;
import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentProviderException;
import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
import biz.ugur.busroutebackend.payment.domain.services.PaymentOrchestrator;
import biz.ugur.busroutebackend.payment.domain.valueobjects.PaymentId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class RefundPaymentUseCase extends BaseUseCase<String, PaymentResponse> {

    private final PaymentRepository paymentRepository;
    private final PaymentOrchestrator paymentOrchestrator;

    public RefundPaymentUseCase(PaymentRepository paymentRepository,
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
                .flatMap(p -> paymentOrchestrator.refund(p, p.getMoney().getAmountMinor())
                        .flatMap(res -> res.success()
                                ? paymentRepository.save(p.markRefunded())
                                : Mono.error(new PaymentProviderException(
                                        res.errorCode(),
                                        "Refund failed: " + res.errorMessage()))))
                .map(PaymentResponse::fromDomain)
                .doOnSuccess(r -> log.info("Payment refunded: id={}", r.id()));
    }

    @Override
    protected String getBoundContext() { return "payment.admin"; }
}
