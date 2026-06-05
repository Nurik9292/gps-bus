package biz.ugur.busroutebackend.subscription.application.usecase.admin;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import biz.ugur.busroutebackend.payment.domain.model.Payment;
import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
import biz.ugur.busroutebackend.payment.domain.valueobjects.Money;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionPeriod;
import biz.ugur.busroutebackend.subscription.domain.model.Subscription;
import biz.ugur.busroutebackend.subscription.domain.repository.SubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetClientTransactionsUseCaseTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    private GetClientTransactionsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetClientTransactionsUseCase(
                subscriptionRepository, paymentRepository, correlationService, eventBus);
        lenient().when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private Payment payment() {
        return Payment.register(
                PaymentProvider.HALK,
                PaymentSubjectType.CLIENT_SUBSCRIPTION,
                "sub-1",
                null,
                Money.ofMinor(400L, "TMT"),
                "https://example.com/return/HALK",
                LocalDateTime.now().plusMinutes(30));
    }

    @Test
    void withSubscriptions_returnsTransactionsForClient() {
        Subscription sub = Subscription.initiate("client-1", SubscriptionPeriod.MONTHLY, 400, "TMT");
        when(subscriptionRepository.findAllByClientId("client-1")).thenReturn(Flux.just(sub));
        when(paymentRepository.findBySubjectTypeAndSubjectIdIn(any(), any(), any(), any(), any(), any()))
                .thenReturn(Flux.just(payment()));
        when(paymentRepository.countBySubjectTypeAndSubjectIdIn(any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(1L));

        StepVerifier.create(useCase.execute(
                        new GetClientTransactionsUseCase.Query("client-1", 1, 20, null, null, null)))
                .assertNext(list -> {
                    assertThat(list.items()).hasSize(1);
                    assertThat(list.pagination().getTotalItems()).isEqualTo(1L);
                })
                .verifyComplete();
    }

    @Test
    void noSubscriptions_returnsEmptyWithoutQueryingPayments() {
        when(subscriptionRepository.findAllByClientId("client-2")).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(
                        new GetClientTransactionsUseCase.Query("client-2", 1, 20, null, null, null)))
                .assertNext(list -> {
                    assertThat(list.items()).isEmpty();
                    assertThat(list.pagination().getTotalItems()).isEqualTo(0L);
                })
                .verifyComplete();

        verify(paymentRepository, never())
                .findBySubjectTypeAndSubjectIdIn(any(), any(), any(), any(), any(), any());
    }
}
