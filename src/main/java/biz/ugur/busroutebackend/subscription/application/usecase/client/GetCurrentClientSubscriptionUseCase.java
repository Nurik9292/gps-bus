package biz.ugur.busroutebackend.subscription.application.usecase.client;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.subscription.application.dto.SubscriptionResponse;
import biz.ugur.busroutebackend.subscription.domain.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class GetCurrentClientSubscriptionUseCase
        extends BaseUseCase<String, SubscriptionResponse> {

    private final SubscriptionRepository subscriptionRepository;

    public GetCurrentClientSubscriptionUseCase(SubscriptionRepository subscriptionRepository,
                                                CorrelationContextService correlationService,
                                                EventBus eventBus) {
        super(correlationService, eventBus);
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    protected String getBoundContext() {
        return "subscription.client";
    }

    @Override
    protected Mono<SubscriptionResponse> process(String clientId) {
        return subscriptionRepository.findActiveByClientId(clientId)
                .switchIfEmpty(Mono.defer(() -> subscriptionRepository.findLatestByClientId(clientId)))
                .map(SubscriptionResponse::fromDomain);
    }
}
