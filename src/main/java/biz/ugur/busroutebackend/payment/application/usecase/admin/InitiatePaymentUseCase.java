package biz.ugur.busroutebackend.payment.application.usecase.admin;

import biz.ugur.busroutebackend.payment.application.dto.InitiatePaymentCommand;
import biz.ugur.busroutebackend.payment.application.dto.InitiatePaymentResponse;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentValidationException;
import biz.ugur.busroutebackend.payment.domain.model.Payment;
import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
import biz.ugur.busroutebackend.payment.domain.services.PaymentOrchestrator;
import biz.ugur.busroutebackend.payment.domain.valueobjects.Money;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@Slf4j
public class InitiatePaymentUseCase
        extends BaseUseCase<Mono<InitiatePaymentCommand>, InitiatePaymentResponse> {

    private final PaymentRepository paymentRepository;
    private final PaymentOrchestrator paymentOrchestrator;

    public InitiatePaymentUseCase(PaymentRepository paymentRepository,
                                   PaymentOrchestrator paymentOrchestrator,
                                   CorrelationContextService correlationService,
                                   EventBus eventBus) {
        super(correlationService, eventBus);
        this.paymentRepository = paymentRepository;
        this.paymentOrchestrator = paymentOrchestrator;
    }

    @Override
    protected Mono<InitiatePaymentResponse> process(Mono<InitiatePaymentCommand> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "payment.admin";
    }

    private Mono<InitiatePaymentResponse> processInternal(InitiatePaymentCommand cmd) {
        if (cmd.amountMinor() == null || cmd.amountMinor() <= 0) {
            return Mono.error(PaymentValidationException.singleField(
                    "amount_minor", "must be positive"));
        }

        PaymentProvider provider = PaymentProvider.from(cmd.provider());
        PaymentSubjectType subjectType = PaymentSubjectType.from(cmd.subjectType());
        Money money = Money.ofMinor(cmd.amountMinor(),
                cmd.currency() != null ? cmd.currency() : "TMT");
        int expiresInMinutes = cmd.expiresInMinutes() != null ? cmd.expiresInMinutes() : 30;

        Payment draft = Payment.register(
                provider, subjectType, cmd.subjectId(), cmd.businessId(),
                money, cmd.returnUrl(),
                LocalDateTime.now().plusMinutes(expiresInMinutes));

        return paymentRepository.save(draft)
                .flatMap(saved -> paymentOrchestrator.register(saved)
                        .flatMap(result -> {
                            Payment withProviderOrder = saved.attachProviderOrder(
                                    result.providerOrderId(), result.formUrl());
                            return paymentRepository.save(withProviderOrder);
                        }))
                .map(p -> new InitiatePaymentResponse(
                        p.getId().getValue(),
                        p.getOrderNumber().getValue(),
                        p.getFormUrl(),
                        p.getProvider().name()))
                .doOnSuccess(r -> log.info("Payment initiated: id={} provider={} subject={}/{}",
                        r.paymentId(), r.provider(), cmd.subjectType(), cmd.subjectId()))
                .doOnError(e -> log.warn("Payment initiation failed: {}", e.getMessage()));
    }
}
