package biz.ugur.busroutebackend.subscription.application.usecase.admin;

import biz.ugur.busroutebackend.client.application.dto.ClientSummary;
import biz.ugur.busroutebackend.client.application.service.ClientLookupService;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionPeriod;
import biz.ugur.busroutebackend.subscription.domain.exceptions.SubscriptionNotFoundException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetSubscriptionByIdUseCaseTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private ClientLookupService clientLookupService;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    private GetSubscriptionByIdUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetSubscriptionByIdUseCase(
                subscriptionRepository, clientLookupService, correlationService, eventBus);
        lenient().when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private Subscription subscription() {
        return Subscription.initiate("client-1", SubscriptionPeriod.MONTHLY, 400, "TMT");
    }

    @Test
    void found_attachesClientSummary() {
        Subscription sub = subscription();
        when(subscriptionRepository.findById(any(SubscriptionId.class))).thenReturn(Mono.just(sub));
        when(clientLookupService.findById("client-1")).thenReturn(Mono.just(
                new ClientSummary("client-1", "Иван", "+99361222333", "ACTIVE", "ANDROID", null)));

        StepVerifier.create(useCase.execute(sub.getId().getValue()))
                .assertNext(r -> {
                    assertThat(r.client()).isNotNull();
                    assertThat(r.client().phone()).isEqualTo("+99361222333");
                    assertThat(r.client().name()).isEqualTo("Иван");
                })
                .verifyComplete();
    }

    @Test
    void clientMissing_returnsResponseWithoutClient() {
        Subscription sub = subscription();
        when(subscriptionRepository.findById(any(SubscriptionId.class))).thenReturn(Mono.just(sub));
        when(clientLookupService.findById("client-1")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(sub.getId().getValue()))
                .assertNext(r -> assertThat(r.client()).isNull())
                .verifyComplete();
    }

    @Test
    void notFound_propagatesError() {
        when(subscriptionRepository.findById(any(SubscriptionId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute("missing"))
                .expectErrorSatisfies(err -> assertInstanceOf(SubscriptionNotFoundException.class, err))
                .verify();
    }
}
