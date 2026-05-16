package biz.ugur.busroutebackend.payment.application.usecase.admin;

import biz.ugur.busroutebackend.payment.application.dto.PaymentResponse;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentNotFoundException;
import biz.ugur.busroutebackend.payment.domain.model.Payment;
import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class ConfirmCashPaymentUseCaseTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-14T12:00:00Z"), ZoneOffset.UTC);

    private ConfirmCashPaymentUseCase useCase;

    private Payment cashPayment;

    @BeforeEach
    void setUp() {
        useCase = new ConfirmCashPaymentUseCase(paymentRepository, clock, correlationService, eventBus);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));

        cashPayment = Payment.register(
                PaymentProvider.CASH,
                PaymentSubjectType.AD_PLACEMENT,
                "subject-1", null,
                Money.ofMinor(50_000L, "TMT"),
                "https://example.com/return",
                LocalDateTime.now().plusHours(1)
        );
    }

    @Test
    void confirmsAndReturnsCompletedPayment() {
        when(paymentRepository.findById(cashPayment.getId())).thenReturn(Mono.just(cashPayment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(
                        new ConfirmCashPaymentUseCase.Command(cashPayment.getId().getValue(), "admin_user")))
                .assertNext(resp -> {
                    assertThat(resp.id()).isEqualTo(cashPayment.getId().getValue());
                    assertThat(resp.status()).isEqualTo("COMPLETED");
                    assertThat(resp.completedBy()).isEqualTo("admin_user");
                })
                .verifyComplete();
    }

    @Test
    void returnsNotFoundWhenPaymentMissing() {
        String unknownId = PaymentId.generate().getValue();
        when(paymentRepository.findById(any(PaymentId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(
                        new ConfirmCashPaymentUseCase.Command(unknownId, "admin_user")))
                .expectErrorSatisfies(err -> assertInstanceOf(PaymentNotFoundException.class, err))
                .verify();
    }

    @Test
    void exposesBoundContext() {
        assertEquals("payment.admin", useCase.getBoundContext());
    }
}
