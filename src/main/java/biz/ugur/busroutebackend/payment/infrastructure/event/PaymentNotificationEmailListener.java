package biz.ugur.busroutebackend.payment.infrastructure.event;

import biz.ugur.busroutebackend.business.domain.model.Business;
import biz.ugur.busroutebackend.business.domain.repository.BusinessRepository;
import biz.ugur.busroutebackend.business.domain.valueobjects.BusinessId;
import biz.ugur.busroutebackend.payment.domain.events.PaymentCompletedEvent;
import biz.ugur.busroutebackend.payment.domain.events.PaymentFailedEvent;
import biz.ugur.busroutebackend.payment.domain.model.Payment;
import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
import biz.ugur.busroutebackend.payment.domain.valueobjects.PaymentId;
import biz.ugur.busroutebackend.shared.infrastructure.email.EmailNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Cross-cutting concern: emails business owners about payment outcomes.
 *
 * <p>Payment events carry only the paymentId and subject info; we look up the
 * Payment + the linked Business to get the owner's email and the amount.
 * Entirely async — failure to send an email never affects the payment lifecycle.
 */
@Component
@Slf4j
public class PaymentNotificationEmailListener {

    private final PaymentRepository paymentRepository;
    private final BusinessRepository businessRepository;
    private final EmailNotificationService emailService;

    public PaymentNotificationEmailListener(PaymentRepository paymentRepository,
                                             BusinessRepository businessRepository,
                                             EmailNotificationService emailService) {
        this.paymentRepository = paymentRepository;
        this.businessRepository = businessRepository;
        this.emailService = emailService;
    }

    @Async
    @EventListener
    public void onCompleted(PaymentCompletedEvent event) {
        loadPaymentAndBusiness(event.getPaymentId())
                .flatMap(pair -> emailService.sendPaymentCompletedNotification(
                        pair.businessEmail(),
                        pair.businessName(),
                        pair.payment().getMoney().toMajorUnits(),
                        pair.payment().getMoney().getCurrency(),
                        pair.payment().getOrderNumber().getValue()))
                .onErrorResume(this::swallow)
                .subscribe();
    }

    @Async
    @EventListener
    public void onFailed(PaymentFailedEvent event) {
        loadPaymentAndBusiness(event.getPaymentId())
                .flatMap(pair -> emailService.sendPaymentFailedNotification(
                        pair.businessEmail(),
                        pair.businessName(),
                        event.getFailureMessage() != null
                                ? event.getFailureMessage()
                                : event.getTerminalStatus().name(),
                        pair.payment().getOrderNumber().getValue()))
                .onErrorResume(this::swallow)
                .subscribe();
    }

    // -------------------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------------------

    private record PaymentBusinessPair(Payment payment, String businessName, String businessEmail) {}

    private Mono<PaymentBusinessPair> loadPaymentAndBusiness(String paymentId) {
        return paymentRepository.findById(PaymentId.of(paymentId))
                .flatMap(payment -> resolveBusiness(payment)
                        .map(b -> new PaymentBusinessPair(
                                payment,
                                b != null ? b.getName().getValue() : "Business",
                                b != null && b.getContactInfo() != null
                                        ? b.getContactInfo().getEmail() : null)));
    }

    private Mono<Business> resolveBusiness(Payment payment) {
        if (payment.getBusinessId() == null || payment.getBusinessId().isBlank()) {
            return Mono.just((Business) null).cast(Business.class).defaultIfEmpty(null);
        }
        return businessRepository.findById(BusinessId.of(payment.getBusinessId()))
                .defaultIfEmpty(null);
    }

    private Mono<Void> swallow(Throwable e) {
        log.warn("Payment email dispatch failed: {}", e.getMessage());
        return Mono.empty();
    }
}
