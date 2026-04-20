package biz.ugur.busroutebackend.payment.application.usecase.admin;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentNotFoundException;
import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentProviderException;
import biz.ugur.busroutebackend.payment.domain.model.Payment;
import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
import biz.ugur.busroutebackend.payment.domain.services.PaymentOrchestrator;
import biz.ugur.busroutebackend.payment.domain.services.PaymentProviderGateway;
import biz.ugur.busroutebackend.payment.domain.valueobjects.Money;
import biz.ugur.busroutebackend.payment.domain.valueobjects.PaymentId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class RefundPaymentUseCaseTest {

    @InjectMocks
    private RefundPaymentUseCase useCase;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentOrchestrator paymentOrchestrator;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    private Payment completed;

    @BeforeEach
    void setUp() {
        completed = Payment.register(
                PaymentProvider.HALK,
                PaymentSubjectType.AD_PLACEMENT,
                "subject-1", null,
                Money.ofMinor(10_000L, "TMT"),
                "https://example.com/return",
                LocalDateTime.now().plusHours(1)
        ).attachProviderOrder("order-1", "https://pay/form")
         .markCompleted("****1111", "12/26", "John");
    }

    @Test
    void refundsSuccessfulPayment() {
        PaymentProviderGateway.ActionResult success = new PaymentProviderGateway.ActionResult(true, null, null);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findById(completed.getId())).thenReturn(Mono.just(completed));
        when(paymentOrchestrator.refund(any(Payment.class), anyLong())).thenReturn(Mono.just(success));
        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(completed.getId().getValue()))
                .assertNext(r -> assertEquals(completed.getId().getValue(), r.id()))
                .verifyComplete();
    }

    @Test
    void errorsWhenProviderFails() {
        PaymentProviderGateway.ActionResult failure = new PaymentProviderGateway.ActionResult(false, "E001", "gateway down");

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findById(completed.getId())).thenReturn(Mono.just(completed));
        when(paymentOrchestrator.refund(any(Payment.class), anyLong())).thenReturn(Mono.just(failure));

        StepVerifier.create(useCase.execute(completed.getId().getValue()))
                .expectErrorSatisfies(err -> assertInstanceOf(PaymentProviderException.class, err))
                .verify();
    }

    @Test
    void errorsWhenPaymentNotFound() {
        String id = PaymentId.generate().getValue();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(paymentRepository.findById(any(PaymentId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(id))
                .expectErrorSatisfies(err -> assertInstanceOf(PaymentNotFoundException.class, err))
                .verify();
    }

    @Test
    void exposesBoundContext() {
        assertEquals("payment.admin", useCase.getBoundContext());
    }
}
