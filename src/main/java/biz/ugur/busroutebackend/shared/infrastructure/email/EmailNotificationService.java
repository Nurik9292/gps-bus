package biz.ugur.busroutebackend.shared.infrastructure.email;

import reactor.core.publisher.Mono;

public interface EmailNotificationService {

    Mono<Void> sendComplaintNotification(String title, String type, String description);
}
