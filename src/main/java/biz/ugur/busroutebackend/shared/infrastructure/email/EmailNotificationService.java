package biz.ugur.busroutebackend.shared.infrastructure.email;

import reactor.core.publisher.Mono;

/**
 * Email dispatcher port. Two implementations:
 *   • {@link SmtpEmailNotificationService}  — active when {@code app.mail.enabled=true}
 *   • {@link NoOpEmailNotificationService}  — default, just logs
 *
 * <p>Listeners in domain modules subscribe to domain events (via @EventListener) and
 * call the matching method here. Email delivery is best-effort — failures are logged
 * and swallowed so they never break the business flow.
 */
public interface EmailNotificationService {

    Mono<Void> sendComplaintNotification(String title, String type, String description);

    /** Admin alert: a new business has submitted an onboarding application. */
    Mono<Void> sendBusinessSubmittedAlert(String businessName,
                                           String businessType,
                                           String contactPhone,
                                           String contactEmail);

    /** Business owner notification: application approved. */
    Mono<Void> sendBusinessApprovedNotification(String recipientEmail, String businessName);

    /** Business owner notification: application rejected. */
    Mono<Void> sendBusinessRejectedNotification(String recipientEmail,
                                                 String businessName,
                                                 String reason);

    /** Business owner notification: business suspended by admin. */
    Mono<Void> sendBusinessSuspendedNotification(String recipientEmail,
                                                  String businessName,
                                                  String reason);

    /** Business owner notification: a payment finished successfully. */
    Mono<Void> sendPaymentCompletedNotification(String recipientEmail,
                                                 String businessName,
                                                 double amount,
                                                 String currency,
                                                 String orderNumber);

    /** Business owner notification: payment failed, placement won't go live. */
    Mono<Void> sendPaymentFailedNotification(String recipientEmail,
                                              String businessName,
                                              String reason,
                                              String orderNumber);
}
