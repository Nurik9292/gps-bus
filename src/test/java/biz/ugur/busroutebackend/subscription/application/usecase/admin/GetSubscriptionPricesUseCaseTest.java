package biz.ugur.busroutebackend.subscription.application.usecase.admin;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionPeriod;
import biz.ugur.busroutebackend.subscription.domain.model.SubscriptionPlanPrice;
import biz.ugur.busroutebackend.subscription.domain.repository.SubscriptionPlanPriceRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSubscriptionPricesUseCaseTest {

    @Mock
    private SubscriptionPlanPriceRepository priceRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    private GetSubscriptionPricesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetSubscriptionPricesUseCase(priceRepository, correlationService, eventBus);
        lenient().when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private SubscriptionPlanPrice price(SubscriptionPeriod period, long amount) {
        return SubscriptionPlanPrice.restore(period, amount, "TMT", "seed",
                LocalDateTime.now(), LocalDateTime.now(), 1L);
    }

    @Test
    void returnsAllPrices() {
        when(priceRepository.findAllPrices()).thenReturn(Flux.just(
                price(SubscriptionPeriod.MONTHLY, 400),
                price(SubscriptionPeriod.YEARLY, 4000)));

        StepVerifier.create(useCase.execute(null))
                .assertNext(list -> {
                    assertThat(list).hasSize(2);
                    assertThat(list.get(0).period()).isEqualTo("MONTHLY");
                    assertThat(list.get(0).amountMajor()).isEqualTo(4.0);
                    assertThat(list.get(1).period()).isEqualTo("YEARLY");
                    assertThat(list.get(1).amountMinor()).isEqualTo(4000);
                })
                .verifyComplete();
    }
}
