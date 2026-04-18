package biz.ugur.busroutebackend.advertising.infrastructure.event;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import biz.ugur.busroutebackend.advertising.domain.model.AdPlacement;
import biz.ugur.busroutebackend.advertising.domain.repository.AdPlacementRepository;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import biz.ugur.busroutebackend.payment.domain.events.PaymentCompletedEvent;
import biz.ugur.busroutebackend.payment.domain.events.PaymentFailedEvent;
import biz.ugur.busroutebackend.payment.domain.events.PaymentRefundedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


@Component
@Slf4j
public class PaymentEventListener {

    private final AdPlacementRepository placementRepository;

    public PaymentEventListener(AdPlacementRepository placementRepository) {
        this.placementRepository = placementRepository;
    }

    @Async
    @EventListener
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        if (event.getSubjectType() != PaymentSubjectType.AD_PLACEMENT) return;
        transitionPlacement(event.getSubjectId(), AdPlacement::markAsScheduled,
                PlacementStatus.SCHEDULED, "payment completed");
    }

    @Async
    @EventListener
    public void onPaymentFailed(PaymentFailedEvent event) {
        if (event.getSubjectType() != PaymentSubjectType.AD_PLACEMENT) return;
        if (event.getTerminalStatus() == PaymentStatus.EXPIRED
                || event.getTerminalStatus() == PaymentStatus.CANCELLED
                || event.getTerminalStatus() == PaymentStatus.DECLINED) {
            transitionPlacement(event.getSubjectId(), AdPlacement::cancel,
                    PlacementStatus.CANCELLED, "payment " + event.getTerminalStatus());
        }
    }

    @Async
    @EventListener
    public void onPaymentRefunded(PaymentRefundedEvent event) {
        if (event.getSubjectType() != PaymentSubjectType.AD_PLACEMENT) return;
        transitionPlacement(event.getSubjectId(), AdPlacement::cancel,
                PlacementStatus.CANCELLED, "payment refunded");
    }

    private void transitionPlacement(String placementId,
                                      java.util.function.UnaryOperator<AdPlacement> transition,
                                      PlacementStatus expected,
                                      String reason) {
        placementRepository.findById(PlacementId.of(placementId))
                .flatMap(p -> {
                    if (p.getStatus() == expected) {
                        log.debug("Placement {} already in {} — skipping", placementId, expected);
                        return Mono.just(p);
                    }
                    try {
                        AdPlacement next = transition.apply(p);
                        return placementRepository.save(next)
                                .doOnSuccess(saved -> log.info(
                                        "AdPlacement {} → {} (reason: {})",
                                        placementId, expected, reason));
                    } catch (Exception e) {
                        log.warn("Cannot transition AdPlacement {} to {} (current={}): {}",
                                placementId, expected, p.getStatus(), e.getMessage());
                        return Mono.just(p);
                    }
                })
                .onErrorResume(e -> {
                    log.warn("Failed to react to payment event for placement {}: {}",
                            placementId, e.getMessage());
                    return Mono.empty();
                })
                .subscribe();
    }
}
