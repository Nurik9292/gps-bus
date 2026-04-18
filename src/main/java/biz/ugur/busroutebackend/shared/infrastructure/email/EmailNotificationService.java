package biz.ugur.busroutebackend.shared.infrastructure.email;

import reactor.core.publisher.Mono;


public interface EmailNotificationService {

    Mono<Void> sendComplaintNotification(String title, String type, String description);

    Mono<Void> sendBusinessSubmittedAlert(String businessName,
                                           String businessType,
                                           String contactPhone,
                                           String contactEmail);

    Mono<Void> sendBusinessApprovedNotification(String recipientEmail, String businessName);

    Mono<Void> sendBusinessRejectedNotification(String recipientEmail,
                                                 String businessName,
                                                 String reason);

    Mono<Void> sendBusinessSuspendedNotification(String recipientEmail,
                                                  String businessName,
                                                  String reason);

    Mono<Void> sendPaymentCompletedNotification(String recipientEmail,
                                                 String businessName,
                                                 double amount,
                                                 String currency,
                                                 String orderNumber);

    Mono<Void> sendPaymentFailedNotification(String recipientEmail,
                                              String businessName,
                                              String reason,
                                              String orderNumber);
}
