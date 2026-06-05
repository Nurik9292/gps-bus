package biz.ugur.busroutebackend.subscription.application.usecase.client;

import biz.ugur.busroutebackend.payment.application.dto.PaymentResponse;
import biz.ugur.busroutebackend.payment.application.usecase.admin.GetPaymentByIdUseCase;
import biz.ugur.busroutebackend.payment.application.usecase.admin.SyncPaymentStatusUseCase;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import biz.ugur.busroutebackend.payment.domain.model.Payment;
import biz.ugur.busroutebackend.payment.domain.valueobjects.Money;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionPeriod;
import biz.ugur.busroutebackend.subscription.domain.model.Subscription;
import biz.ugur.busroutebackend.subscription.domain.repository.SubscriptionRepository;
import biz.ugur.busroutebackend.subscription.infrastructure.config.SubscriptionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
class GetCurrentClientSubscriptionUseCaseTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SyncPaymentStatusUseCase syncPaymentStatusUseCase;

    @Mock
    private GetPaymentByIdUseCase getPaymentByIdUseCase;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    private GetCurrentClientSubscriptionUseCase useCase;

    @BeforeEach
    void setUp() {
        SubscriptionProperties properties = new SubscriptionProperties();
        useCase = new GetCurrentClientSubscriptionUseCase(
                subscriptionRepository, syncPaymentStatusUseCase, getPaymentByIdUseCase,
                properties, correlationService, eventBus);
        lenient().when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private Subscription subscriptionWithPayment() {
        return Subscription.initiate("client-1", SubscriptionPeriod.MONTHLY, 400, "TMT")
                .attachPayment("pay-1");
    }

    private Payment basePayment() {
        return Payment.register(PaymentProvider.HALK, PaymentSubjectType.CLIENT_SUBSCRIPTION,
                "sub-1", null, Money.ofMinor(400L, "TMT"),
                "https://example.com/return/HALK", LocalDateTime.now().plusMinutes(30));
    }

    @Test
    void onReadSync_completed_mapsToSuccessWithLastTransaction() {
        PaymentResponse completed = PaymentResponse.fromDomain(
                basePayment().markCompleted("411111****1111", "12/29", "John Doe"));
        when(subscriptionRepository.findActiveByClientId("client-1"))
                .thenReturn(Mono.just(subscriptionWithPayment()));
        when(syncPaymentStatusUseCase.execute("pay-1")).thenReturn(Mono.just(completed));

        StepVerifier.create(useCase.execute("client-1"))
                .assertNext(response -> {
                    assertThat(response.paymentStatus()).isEqualTo("SUCCESS");
                    assertThat(response.lastTransaction()).isNotNull();
                    assertThat(response.lastTransaction().status()).isEqualTo("COMPLETED");
                })
                .verifyComplete();
    }

    @Test
    void onReadSyncError_fallsBackToStoredStatus() {
        PaymentResponse stored = PaymentResponse.fromDomain(basePayment());
        when(subscriptionRepository.findActiveByClientId("client-1"))
                .thenReturn(Mono.just(subscriptionWithPayment()));
        when(syncPaymentStatusUseCase.execute("pay-1"))
                .thenReturn(Mono.error(new RuntimeException("bank timeout")));
        when(getPaymentByIdUseCase.execute("pay-1")).thenReturn(Mono.just(stored));

        StepVerifier.create(useCase.execute("client-1"))
                .assertNext(response -> {
                    assertThat(response.paymentStatus()).isEqualTo("PENDING");
                    assertThat(response.lastTransaction().status()).isEqualTo("REGISTERED");
                })
                .verifyComplete();

        verify(getPaymentByIdUseCase).execute("pay-1");
    }

    @Test
    void noPaymentId_returnsPlainResponseWithoutSync() {
        Subscription noPayment = Subscription.initiate("client-1", SubscriptionPeriod.MONTHLY, 400, "TMT");
        when(subscriptionRepository.findActiveByClientId("client-1")).thenReturn(Mono.just(noPayment));

        StepVerifier.create(useCase.execute("client-1"))
                .assertNext(response -> {
                    assertThat(response.paymentStatus()).isNull();
                    assertThat(response.lastTransaction()).isNull();
                })
                .verifyComplete();

        verify(syncPaymentStatusUseCase, never()).execute(anyString());
    }
}
