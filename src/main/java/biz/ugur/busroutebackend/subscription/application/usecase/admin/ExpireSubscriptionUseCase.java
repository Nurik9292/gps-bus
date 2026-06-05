package biz.ugur.busroutebackend.subscription.application.usecase.admin;

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
public class ExpireSubscriptionUseCase extends BaseUseCase<String, SubscriptionResponse> {

    private final SubscriptionRepository subscriptionRepository;

    public ExpireSubscriptionUseCase(SubscriptionRepository subscriptionRepository,
                                     CorrelationContextService correlationService,
                                     EventBus eventBus) {
        super(correlationService, eventBus);
        this.subscriptionRepository = subscriptionRepository;
    }

    @Override
    protected Mono<SubscriptionResponse> process(String subscriptionId) {
        return subscriptionRepository.findById(SubscriptionId.of(subscriptionId))
                .switchIfEmpty(Mono.error(new SubscriptionNotFoundException(
                        "subscription not found: " + subscriptionId)))
                .flatMap(this::expireIfActive)
                .map(SubscriptionResponse::fromDomain);
    }

    private Mono<Subscription> expireIfActive(Subscription subscription) {
        if (!subscription.isActive()) {
            return Mono.just(subscription);
        }
        Subscription expired = subscription.expire();
        return subscriptionRepository.save(expired)
                .doOnSuccess(saved -> {
                    expired.getDomainEvents().forEach(eventBus::publish);
                    expired.clearEvents();
                });
    }

    @Override
    protected String getBoundContext() { return "subscription.admin"; }
}
