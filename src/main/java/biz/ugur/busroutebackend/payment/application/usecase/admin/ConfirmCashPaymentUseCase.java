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

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class ConfirmCashPaymentUseCase
        extends BaseUseCase<ConfirmCashPaymentUseCase.Command, PaymentResponse> {

    public record Command(String paymentId, String confirmedBy) {}

    private final PaymentRepository paymentRepository;
    private final Clock clock;

    public ConfirmCashPaymentUseCase(PaymentRepository paymentRepository,
                                     Clock clock,
                                     CorrelationContextService correlationService,
                                     EventBus eventBus) {
        super(correlationService, eventBus);
        this.paymentRepository = paymentRepository;
        this.clock = clock;
    }

    @Override
    protected Mono<PaymentResponse> process(Command cmd) {
        return paymentRepository.findById(PaymentId.of(cmd.paymentId()))
                .switchIfEmpty(Mono.error(new PaymentNotFoundException(cmd.paymentId())))
                .map(p -> p.confirmCash(cmd.confirmedBy(), LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)))
                .flatMap(paymentRepository::save)
                .transform(this::persistAndPublish)
                .map(PaymentResponse::fromDomain);
    }

    @Override
    protected String getBoundContext() { return "payment.admin"; }
}
