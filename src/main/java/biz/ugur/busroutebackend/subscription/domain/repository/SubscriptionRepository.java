package biz.ugur.busroutebackend.subscription.domain.repository;

import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionPeriod;
import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionStatus;
import biz.ugur.busroutebackend.subscription.domain.model.Subscription;
import biz.ugur.busroutebackend.subscription.domain.valueobjects.SubscriptionId;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SubscriptionRepository {

    Mono<Subscription> save(Subscription subscription);

    Mono<Subscription> findById(SubscriptionId id);

    Mono<Subscription> findActiveByClientId(String clientId);

    Mono<Subscription> findLatestByClientId(String clientId);

    Mono<Subscription> findByPaymentId(String paymentId);

    Flux<Subscription> findPaginated(SubscriptionStatus status, SubscriptionPeriod period, Pageable pageable);

    Mono<Long> countFiltered(SubscriptionStatus status, SubscriptionPeriod period);

    Flux<Subscription> findAllByClientId(String clientId);

    Flux<Subscription> findExpiredActive(Pageable pageable);
}
