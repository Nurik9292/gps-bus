package biz.ugur.busroutebackend.shared.infrastructure.email;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpEmailNotificationService implements EmailNotificationService {

    @Override
    public Mono<Void> sendComplaintNotification(String title, String type, String description) {
        log.debug("[email/off] complaint — {}", title);
        return Mono.empty();
    }

    @Override
    public Mono<Void> sendBusinessSubmittedAlert(String businessName, String businessType,
                                                  String contactPhone, String contactEmail) {
        log.debug("[email/off] business-submitted — {}", businessName);
        return Mono.empty();
    }

    @Override
    public Mono<Void> sendBusinessApprovedNotification(String recipientEmail, String businessName) {
        log.debug("[email/off] business-approved — {} → {}", businessName, recipientEmail);
        return Mono.empty();
    }

    @Override
    public Mono<Void> sendBusinessRejectedNotification(String recipientEmail,
                                                        String businessName, String reason) {
        log.debug("[email/off] business-rejected — {} → {}", businessName, recipientEmail);
        return Mono.empty();
    }

    @Override
    public Mono<Void> sendBusinessSuspendedNotification(String recipientEmail,
                                                         String businessName, String reason) {
        log.debug("[email/off] business-suspended — {} → {}", businessName, recipientEmail);
        return Mono.empty();
    }

    @Override
    public Mono<Void> sendPaymentCompletedNotification(String recipientEmail, String businessName,
                                                        double amount, String currency,
                                                        String orderNumber) {
        log.debug("[email/off] payment-completed — {} {} {} → {}",
                orderNumber, amount, currency, recipientEmail);
        return Mono.empty();
    }

    @Override
    public Mono<Void> sendPaymentFailedNotification(String recipientEmail, String businessName,
                                                     String reason, String orderNumber) {
        log.debug("[email/off] payment-failed — {} → {}", orderNumber, recipientEmail);
        return Mono.empty();
    }
}
