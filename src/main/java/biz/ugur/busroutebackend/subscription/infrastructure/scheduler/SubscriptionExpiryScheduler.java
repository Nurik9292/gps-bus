package biz.ugur.busroutebackend.subscription.infrastructure.scheduler;

import biz.ugur.busroutebackend.subscription.application.usecase.admin.ExpireSubscriptionUseCase;
import biz.ugur.busroutebackend.subscription.domain.repository.SubscriptionRepository;
import biz.ugur.busroutebackend.subscription.infrastructure.config.SubscriptionSchedulerProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@Component
@Slf4j
@ConditionalOnProperty(
        prefix = "business.subscription.expiry-scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
public class SubscriptionExpiryScheduler {

    private static final int CONCURRENCY = 4;
    private static final Duration TICK_TIMEOUT = Duration.ofMinutes(1);
    private static final Duration PER_ITEM_TIMEOUT = Duration.ofSeconds(10);

    private final SubscriptionRepository subscriptionRepository;
    private final ExpireSubscriptionUseCase expireSubscriptionUseCase;
    private final SubscriptionSchedulerProperties properties;

    public SubscriptionExpiryScheduler(SubscriptionRepository subscriptionRepository,
                                       ExpireSubscriptionUseCase expireSubscriptionUseCase,
                                       SubscriptionSchedulerProperties properties) {
        this.subscriptionRepository = subscriptionRepository;
        this.expireSubscriptionUseCase = expireSubscriptionUseCase;
        this.properties = properties;
    }

    @Scheduled(
            fixedDelayString = "${business.subscription.expiry-scheduler.fixed-delay-ms:300000}",
            initialDelayString = "${business.subscription.expiry-scheduler.initial-delay-ms:60000}")
    public void tick() {
        subscriptionRepository.findExpiredActive(PageRequest.of(0, properties.getBatchSize()))
                .flatMap(subscription -> expireSubscriptionUseCase.execute(subscription.getId().getValue())
                        .timeout(PER_ITEM_TIMEOUT)
                        .doOnSuccess(result -> log.debug("Auto-expired subscription {}", result.subscriptionId()))
                        .onErrorResume(e -> {
                            log.warn("Auto-expire failed for subscription {}: {}",
                                    subscription.getId().getValue(), e.getMessage());
                            return Mono.empty();
                        }), CONCURRENCY)
                .then()
                .timeout(TICK_TIMEOUT)
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(err -> log.warn("[SCHEDULER] SubscriptionExpiry tick failed: {}", err.toString()))
                .onErrorResume(err -> Mono.empty())
                .subscribe();
    }
}
