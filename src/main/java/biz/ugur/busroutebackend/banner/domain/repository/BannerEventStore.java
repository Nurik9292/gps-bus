package biz.ugur.busroutebackend.banner.domain.repository;

import biz.ugur.busroutebackend.banner.domain.events.BannerDomainEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface BannerEventStore {

    Mono<BannerDomainEvent> save(BannerDomainEvent event);

    Flux<BannerDomainEvent> findByBannerId(String bannerId);

    Flux<BannerDomainEvent> findByBannerIdAfter(String bannerId, java.time.Instant since);

    Flux<BannerDomainEvent> findByEventType(String eventType);

    Mono<Long> countByBannerId(String bannerId);

    Flux<BannerDomainEvent> findAll();
}
