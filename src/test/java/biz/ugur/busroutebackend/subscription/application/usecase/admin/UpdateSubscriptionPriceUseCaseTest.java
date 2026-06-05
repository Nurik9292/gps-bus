package biz.ugur.busroutebackend.subscription.application.usecase.admin;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import biz.ugur.busroutebackend.subscription.application.dto.UpdateSubscriptionPriceCommand;
import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionPeriod;
import biz.ugur.busroutebackend.subscription.domain.exceptions.SubscriptionNotFoundException;
import biz.ugur.busroutebackend.subscription.domain.exceptions.SubscriptionValidationException;
import biz.ugur.busroutebackend.subscription.domain.model.SubscriptionPlanPrice;
import biz.ugur.busroutebackend.subscription.domain.repository.SubscriptionPlanPriceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateSubscriptionPriceUseCaseTest {

    @Mock
    private SubscriptionPlanPriceRepository priceRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    private UpdateSubscriptionPriceUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new UpdateSubscriptionPriceUseCase(priceRepository, correlationService, eventBus);
        lenient().when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        lenient().when(priceRepository.save(any(SubscriptionPlanPrice.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    private SubscriptionPlanPrice existing(long amount) {
        return SubscriptionPlanPrice.restore(SubscriptionPeriod.MONTHLY, amount, "TMT", "seed",
                LocalDateTime.now(), LocalDateTime.now(), 1L);
    }

    @Test
    void update_changesAmount_savesAndPublishesEvent() {
        when(priceRepository.findByPeriod(SubscriptionPeriod.MONTHLY)).thenReturn(Mono.just(existing(400)));

        StepVerifier.create(useCase.execute(new UpdateSubscriptionPriceCommand("MONTHLY", 500, "admin")))
                .assertNext(response -> {
                    assertThat(response.period()).isEqualTo("MONTHLY");
                    assertThat(response.amountMinor()).isEqualTo(500);
                    assertThat(response.updatedBy()).isEqualTo("admin");
                })
                .verifyComplete();

        verify(priceRepository, times(1)).save(any(SubscriptionPlanPrice.class));
        verify(eventBus, times(1)).publish((DomainEvent) any());
    }

    @Test
    void update_unknownPeriod_propagatesValidationError() {
        StepVerifier.create(useCase.execute(new UpdateSubscriptionPriceCommand("weekly", 500, "admin")))
                .expectErrorSatisfies(err -> assertInstanceOf(SubscriptionValidationException.class, err))
                .verify();
    }

    @Test
    void update_priceNotFound_propagatesNotFound() {
        when(priceRepository.findByPeriod(SubscriptionPeriod.YEARLY)).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(new UpdateSubscriptionPriceCommand("YEARLY", 500, "admin")))
                .expectErrorSatisfies(err -> assertInstanceOf(SubscriptionNotFoundException.class, err))
                .verify();
    }

    @Test
    void update_nonPositiveAmount_propagatesValidationError() {
        when(priceRepository.findByPeriod(SubscriptionPeriod.MONTHLY)).thenReturn(Mono.just(existing(400)));

        StepVerifier.create(useCase.execute(new UpdateSubscriptionPriceCommand("MONTHLY", 0, "admin")))
                .expectErrorSatisfies(err -> assertInstanceOf(SubscriptionValidationException.class, err))
                .verify();
    }
}
