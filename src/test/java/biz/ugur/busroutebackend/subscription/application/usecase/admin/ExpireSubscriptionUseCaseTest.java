package biz.ugur.busroutebackend.subscription.application.usecase.admin;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionPeriod;
import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionStatus;
import biz.ugur.busroutebackend.subscription.domain.model.Subscription;
import biz.ugur.busroutebackend.subscription.domain.repository.SubscriptionRepository;
import biz.ugur.busroutebackend.subscription.domain.valueobjects.SubscriptionId;
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
class ExpireSubscriptionUseCaseTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    private ExpireSubscriptionUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ExpireSubscriptionUseCase(subscriptionRepository, correlationService, eventBus);
        lenient().when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    private Subscription active() {
        return Subscription.initiate("client-1", SubscriptionPeriod.MONTHLY, 400, "TMT")
                .attachPayment("pay-1")
                .activate(LocalDateTime.now().minusDays(40));
    }

    @Test
    void active_pastExpiry_transitionsToExpired() {
        Subscription active = active();
        when(subscriptionRepository.findById(any(SubscriptionId.class))).thenReturn(Mono.just(active));

        StepVerifier.create(useCase.execute(active.getId().getValue()))
                .assertNext(r -> assertThat(r.status()).isEqualTo(SubscriptionStatus.EXPIRED.name()))
                .verifyComplete();

        verify(subscriptionRepository).save(any(Subscription.class));
    }

    @Test
    void notActive_isNoOp() {
        Subscription pending = Subscription.initiate("client-1", SubscriptionPeriod.MONTHLY, 400, "TMT");
        when(subscriptionRepository.findById(any(SubscriptionId.class))).thenReturn(Mono.just(pending));

        StepVerifier.create(useCase.execute(pending.getId().getValue()))
                .assertNext(r -> assertThat(r.status()).isEqualTo(SubscriptionStatus.PENDING.name()))
                .verifyComplete();

        verify(subscriptionRepository, never()).save(any(Subscription.class));
    }
}
