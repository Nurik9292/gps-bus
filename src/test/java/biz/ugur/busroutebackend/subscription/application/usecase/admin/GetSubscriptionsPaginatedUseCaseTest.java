package biz.ugur.busroutebackend.subscription.application.usecase.admin;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionPeriod;
import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionStatus;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSubscriptionsPaginatedUseCaseTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    private GetSubscriptionsPaginatedUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetSubscriptionsPaginatedUseCase(subscriptionRepository, correlationService, eventBus);
        lenient().when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private Subscription sub() {
        return Subscription.initiate("client-1", SubscriptionPeriod.MONTHLY, 400, "TMT");
    }

    @Test
    void noFilter_returnsListWithTotalsAndActiveCount() {
        when(subscriptionRepository.findPaginated(isNull(), isNull(), any())).thenReturn(Flux.just(sub(), sub()));
        when(subscriptionRepository.countFiltered(isNull(), isNull())).thenReturn(Mono.just(5L));
        when(subscriptionRepository.countFiltered(eq(SubscriptionStatus.ACTIVE), isNull())).thenReturn(Mono.just(2L));

        StepVerifier.create(useCase.execute(new GetSubscriptionsPaginatedUseCase.Query(1, 20, null, null)))
                .assertNext(list -> {
                    assertThat(list.items()).hasSize(2);
                    assertThat(list.activeCount()).isEqualTo(2L);
                    assertThat(list.pagination().getTotalItems()).isEqualTo(5L);
                })
                .verifyComplete();
    }

    @Test
    void withStatusAndPeriodFilter_passesParsedEnumsToRepository() {
        when(subscriptionRepository.findPaginated(eq(SubscriptionStatus.ACTIVE), eq(SubscriptionPeriod.MONTHLY), any()))
                .thenReturn(Flux.just(sub()));
        when(subscriptionRepository.countFiltered(eq(SubscriptionStatus.ACTIVE), eq(SubscriptionPeriod.MONTHLY)))
                .thenReturn(Mono.just(1L));
        when(subscriptionRepository.countFiltered(eq(SubscriptionStatus.ACTIVE), isNull())).thenReturn(Mono.just(1L));

        StepVerifier.create(useCase.execute(new GetSubscriptionsPaginatedUseCase.Query(1, 20, "active", "month")))
                .assertNext(list -> assertThat(list.items()).hasSize(1))
                .verifyComplete();

        verify(subscriptionRepository).findPaginated(eq(SubscriptionStatus.ACTIVE), eq(SubscriptionPeriod.MONTHLY), any());
    }
}
