package biz.ugur.busroutebackend.subscription.application.usecase.admin;

import biz.ugur.busroutebackend.client.application.service.ClientLookupService;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.subscription.application.dto.SubscriptionResponse;
import biz.ugur.busroutebackend.subscription.domain.exceptions.SubscriptionNotFoundException;
import biz.ugur.busroutebackend.subscription.domain.model.Subscription;
import biz.ugur.busroutebackend.subscription.domain.repository.SubscriptionRepository;
import biz.ugur.busroutebackend.subscription.domain.valueobjects.SubscriptionId;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class GetSubscriptionByIdUseCase extends BaseUseCase<String, SubscriptionResponse> {

    private final SubscriptionRepository subscriptionRepository;
    private final ClientLookupService clientLookupService;

    public GetSubscriptionByIdUseCase(SubscriptionRepository subscriptionRepository,
                                      ClientLookupService clientLookupService,
                                      CorrelationContextService correlationService,
                                      EventBus eventBus) {
        super(correlationService, eventBus);
        this.subscriptionRepository = subscriptionRepository;
        this.clientLookupService = clientLookupService;
    }

    @Override
    protected Mono<SubscriptionResponse> process(String subscriptionId) {
        return subscriptionRepository.findById(SubscriptionId.of(subscriptionId))
                .switchIfEmpty(Mono.error(new SubscriptionNotFoundException(
                        "subscription not found: " + subscriptionId)))
                .flatMap(this::attachClient);
    }

    private Mono<SubscriptionResponse> attachClient(Subscription subscription) {
        return clientLookupService.findById(subscription.getClientId())
                .map(client -> SubscriptionResponse.fromDomain(subscription).withClient(client))
                .defaultIfEmpty(SubscriptionResponse.fromDomain(subscription));
    }

    @Override
    protected String getBoundContext() { return "subscription.admin"; }
}
