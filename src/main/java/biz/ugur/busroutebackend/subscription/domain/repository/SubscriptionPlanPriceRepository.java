package biz.ugur.busroutebackend.subscription.domain.repository;

import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionPeriod;
import biz.ugur.busroutebackend.subscription.domain.model.SubscriptionPlanPrice;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SubscriptionPlanPriceRepository {

    Flux<SubscriptionPlanPrice> findAllPrices();

    Mono<SubscriptionPlanPrice> findByPeriod(SubscriptionPeriod period);

    Mono<SubscriptionPlanPrice> save(SubscriptionPlanPrice price);
}
