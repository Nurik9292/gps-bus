package biz.ugur.busroutebackend.subscription.infrastructure.event;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import biz.ugur.busroutebackend.payment.domain.events.PaymentCompletedEvent;
import biz.ugur.busroutebackend.payment.domain.events.PaymentFailedEvent;
import biz.ugur.busroutebackend.payment.domain.events.PaymentRefundedEvent;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.subscription.domain.model.Subscription;
import biz.ugur.busroutebackend.subscription.domain.repository.SubscriptionRepository;
import biz.ugur.busroutebackend.subscription.domain.valueobjects.SubscriptionId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.function.UnaryOperator;

@Component
@Slf4j
public class SubscriptionPaymentEventListener {

    private final SubscriptionRepository subscriptionRepository;
    private final EventBus eventBus;

    public SubscriptionPaymentEventListener(SubscriptionRepository subscriptionRepository,
                                             EventBus eventBus) {
        this.subscriptionRepository = subscriptionRepository;
        this.eventBus = eventBus;
    }

    @Async
    @EventListener
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        if (event.getSubjectType() != PaymentSubjectType.CLIENT_SUBSCRIPTION) return;
        applyTransition(event.getSubjectId(), s -> s.activate(LocalDateTime.now()),
                "payment completed");
    }

    @Async
    @EventListener
    public void onPaymentFailed(PaymentFailedEvent event) {
        if (event.getSubjectType() != PaymentSubjectType.CLIENT_SUBSCRIPTION) return;
        PaymentStatus terminal = event.getTerminalStatus();
        if (terminal == PaymentStatus.EXPIRED
                || terminal == PaymentStatus.CANCELLED
                || terminal == PaymentStatus.DECLINED) {
            applyTransition(event.getSubjectId(),
                    s -> s.cancel("payment " + terminal.name().toLowerCase()),
                    "payment " + terminal);
        }
    }

    @Async
    @EventListener
    public void onPaymentRefunded(PaymentRefundedEvent event) {
        if (event.getSubjectType() != PaymentSubjectType.CLIENT_SUBSCRIPTION) return;
        applyTransition(event.getSubjectId(),
                s -> s.cancel("payment refunded"),
                "payment refunded");
    }

    private void applyTransition(String subscriptionId,
                                  UnaryOperator<Subscription> transition,
                                  String reason) {
        subscriptionRepository.findById(SubscriptionId.of(subscriptionId))
                .flatMap(current -> {
                    try {
                        Subscription next = transition.apply(current);
                        return subscriptionRepository.save(next)
                                .doOnNext(saved -> saved.getDomainEvents().forEach(eventBus::publish))
                                .doOnNext(saved -> {
                                    saved.clearEvents();
                                    log.info("Subscription {} -> {} (reason: {})",
                                            subscriptionId, saved.getStatus(), reason);
                                });
                    } catch (Exception e) {
                        log.warn("Cannot transition subscription {} (current={}): {}",
                                subscriptionId, current.getStatus(), e.getMessage());
                        return Mono.just(current);
                    }
                })
                .onErrorResume(e -> {
                    log.warn("Failed to react to payment event for subscription {}: {}",
                            subscriptionId, e.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }
}
