package biz.ugur.busroutebackend.subscription.application.usecase.admin;

import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetClientPaymentAttemptsUseCaseTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    private GetClientPaymentAttemptsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetClientPaymentAttemptsUseCase(
                subscriptionRepository, paymentRepository, correlationService, eventBus);
        lenient().when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void bucketsStatusesIntoSuccessFailedPending() {
        Subscription sub = Subscription.initiate("client-1", SubscriptionPeriod.MONTHLY, 400, "TMT");
        when(subscriptionRepository.findAllByClientId("client-1")).thenReturn(Flux.just(sub));
        when(paymentRepository.countBySubjectTypeAndSubjectIdInGroupByStatus(any(), anyCollection()))
                .thenReturn(Mono.just(Map.of("COMPLETED", 1L, "DECLINED", 2L, "REGISTERED", 1L)));

        StepVerifier.create(useCase.execute("client-1"))
                .assertNext(summary -> {
                    assertThat(summary.total()).isEqualTo(4L);
                    assertThat(summary.success()).isEqualTo(1L);
                    assertThat(summary.failed()).isEqualTo(2L);
                    assertThat(summary.pending()).isEqualTo(1L);
                })
                .verifyComplete();
    }

    @Test
    void noSubscriptions_returnsZerosWithoutQueryingPayments() {
        when(subscriptionRepository.findAllByClientId("client-2")).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute("client-2"))
                .assertNext(summary -> {
                    assertThat(summary.total()).isZero();
                    assertThat(summary.success()).isZero();
                    assertThat(summary.failed()).isZero();
                    assertThat(summary.pending()).isZero();
                })
                .verifyComplete();

        verify(paymentRepository, never())
                .countBySubjectTypeAndSubjectIdInGroupByStatus(any(), anyCollection());
    }
}
