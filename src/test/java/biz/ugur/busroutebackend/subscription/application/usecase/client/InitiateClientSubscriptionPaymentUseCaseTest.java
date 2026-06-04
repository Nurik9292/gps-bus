package biz.ugur.busroutebackend.subscription.application.usecase.client;

import biz.ugur.busroutebackend.payment.application.dto.InitiatePaymentCommand;
import biz.ugur.busroutebackend.payment.application.dto.InitiatePaymentResponse;
import biz.ugur.busroutebackend.payment.application.usecase.admin.InitiatePaymentUseCase;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.subscription.application.dto.InitiateSubscriptionPaymentCommand;
import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionStatus;
import biz.ugur.busroutebackend.subscription.domain.exceptions.SubscriptionValidationException;
import biz.ugur.busroutebackend.subscription.domain.model.Subscription;
import biz.ugur.busroutebackend.subscription.domain.repository.SubscriptionRepository;
import biz.ugur.busroutebackend.subscription.infrastructure.config.SubscriptionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitiateClientSubscriptionPaymentUseCaseTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private InitiatePaymentUseCase initiatePaymentUseCase;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    private SubscriptionProperties properties;
    private InitiateClientSubscriptionPaymentUseCase useCase;

    @BeforeEach
    void setUp() {
        properties = new SubscriptionProperties();
        properties.setMonthlyAmountMinor(400);
        properties.setYearlyAmountMinor(4000);
        properties.setCurrency("TMT");
        properties.setPaymentSessionMinutes(30);
        properties.setPaymentReturnUrl("https://admduralga.ulgam.biz/api/v1/payments/return");

        useCase = new InitiateClientSubscriptionPaymentUseCase(
                subscriptionRepository, initiatePaymentUseCase, properties,
                correlationService, eventBus);

        lenient().when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    @Test
    void initiateMonthly_uppercaseProvider_correctAmount_attachesPayment() {
        when(initiatePaymentUseCase.execute(any()))
                .thenReturn(Mono.just(new InitiatePaymentResponse(
                        "pay-001", "ORD-001",
                        "https://bank.example/pay?mdOrder=xyz",
                        "HALK")));

        StepVerifier.create(useCase.execute(
                        new InitiateSubscriptionPaymentCommand("client-1", "halk", "month")))
                .assertNext(response -> {
                    assertThat(response.paymentId()).isEqualTo("pay-001");
                    assertThat(response.formUrl()).isEqualTo("https://bank.example/pay?mdOrder=xyz");
                    assertThat(response.amountMinor()).isEqualTo(400);
                    assertThat(response.currency()).isEqualTo("TMT");
                    assertThat(response.period()).isEqualTo("MONTHLY");
                    assertThat(response.provider()).isEqualTo("HALK");
                    assertThat(response.returnUrl()).isEqualTo("https://admduralga.ulgam.biz/api/v1/payments/return/HALK");
                })
                .verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Mono<InitiatePaymentCommand>> cmdCaptor = ArgumentCaptor.forClass(Mono.class);
        verify(initiatePaymentUseCase).execute(cmdCaptor.capture());
        InitiatePaymentCommand inner = cmdCaptor.getValue().block();
        assertThat(inner).isNotNull();
        assertThat(inner.provider()).isEqualTo("HALK");
        assertThat(inner.subjectType()).isEqualTo("CLIENT_SUBSCRIPTION");
        assertThat(inner.amountMinor()).isEqualTo(400);
        assertThat(inner.currency()).isEqualTo("TMT");
        assertThat(inner.returnUrl()).isEqualTo("https://admduralga.ulgam.biz/api/v1/payments/return/HALK");
        assertThat(inner.failUrl()).isEqualTo("https://admduralga.ulgam.biz/api/v1/payments/return/HALK");
    }

    @Test
    void initiateYearly_usesYearlyAmount() {
        when(initiatePaymentUseCase.execute(any()))
                .thenReturn(Mono.just(new InitiatePaymentResponse(
                        "pay-002", "ORD-002",
                        "https://bank.example/pay?mdOrder=year",
                        "RYSGAL")));

        StepVerifier.create(useCase.execute(
                        new InitiateSubscriptionPaymentCommand("client-1", "rysgal", "year")))
                .assertNext(response -> {
                    assertThat(response.amountMinor()).isEqualTo(4000);
                    assertThat(response.period()).isEqualTo("YEARLY");
                    assertThat(response.provider()).isEqualTo("RYSGAL");
                })
                .verifyComplete();
    }

    @Test
    void initiate_invalidPeriod_propagatesValidationError() {
        StepVerifier.create(useCase.execute(
                        new InitiateSubscriptionPaymentCommand("client-1", "halk", "weekly")))
                .expectErrorSatisfies(err -> assertInstanceOf(SubscriptionValidationException.class, err))
                .verify();
    }

    @Test
    void initiate_savedSubscription_isPendingThenAttachesPayment() {
        when(initiatePaymentUseCase.execute(any()))
                .thenReturn(Mono.just(new InitiatePaymentResponse(
                        "pay-003", "ORD-003", "https://bank/x", "HALK")));

        StepVerifier.create(useCase.execute(
                        new InitiateSubscriptionPaymentCommand("client-1", "halk", "month")))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<Subscription> saveCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository, org.mockito.Mockito.times(2)).save(saveCaptor.capture());
        Subscription firstSave = saveCaptor.getAllValues().get(0);
        Subscription secondSave = saveCaptor.getAllValues().get(1);
        assertThat(firstSave.getStatus()).isEqualTo(SubscriptionStatus.PENDING);
        assertThat(firstSave.getPaymentId()).isNull();
        assertThat(secondSave.getPaymentId()).isEqualTo("pay-003");
    }
}
