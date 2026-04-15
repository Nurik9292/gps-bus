package biz.ugur.busroutebackend.payment.application.usecase.admin;

import biz.ugur.busroutebackend.payment.application.dto.PaymentResponse;
import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentNotFoundException;
import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
import biz.ugur.busroutebackend.payment.domain.valueobjects.PaymentId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class GetPaymentByIdUseCase extends BaseUseCase<String, PaymentResponse> {

    private final PaymentRepository paymentRepository;

    public GetPaymentByIdUseCase(PaymentRepository paymentRepository,
                                  CorrelationContextService correlationService,
                                  EventBus eventBus) {
        super(correlationService, eventBus);
        this.paymentRepository = paymentRepository;
    }

    @Override
    protected Mono<PaymentResponse> process(String paymentId) {
        return paymentRepository.findById(PaymentId.of(paymentId))
                .switchIfEmpty(Mono.error(new PaymentNotFoundException(paymentId)))
                .map(PaymentResponse::fromDomain);
    }

    @Override
    protected String getBoundContext() { return "payment.admin"; }
}
