package biz.ugur.busroutebackend.subscription.domain.repository;

import biz.ugur.busroutebackend.subscription.domain.model.Subscription;
import biz.ugur.busroutebackend.subscription.domain.valueobjects.SubscriptionId;
import reactor.core.publisher.Mono;

public interface SubscriptionRepository {

    Mono<Subscription> save(Subscription subscription);

    Mono<Subscription> findById(SubscriptionId id);

    Mono<Subscription> findActiveByClientId(String clientId);

    Mono<Subscription> findLatestByClientId(String clientId);

    Mono<Subscription> findByPaymentId(String paymentId);
}
