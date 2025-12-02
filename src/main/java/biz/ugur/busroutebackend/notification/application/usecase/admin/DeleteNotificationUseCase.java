package biz.ugur.busroutebackend.notification.application.usecase.admin;

import biz.ugur.busroutebackend.notification.domain.repository.AdminNotificationRepository;
import biz.ugur.busroutebackend.notification.domain.valueobjects.NotificationId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class DeleteNotificationUseCase extends BaseUseCase<Mono<String>, Void> {

    private final AdminNotificationRepository notificationRepository;


    public DeleteNotificationUseCase(AdminNotificationRepository notificationRepository,
                                     CorrelationContextService correlationContextService,
                                     EventBus eventBus) {
        super(correlationContextService, eventBus);
        this.notificationRepository = notificationRepository;
    }


    @Override
    protected Mono<Void> process(Mono<String> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<Void> processInternal(String notificationId) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Deleting notification CorrelationId: {} - NotificationId: {}", correlationId, notificationId);

            return notificationRepository.findById(NotificationId.of(notificationId))
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Notification not found: " + notificationId)))
                    .flatMap(notification -> notificationRepository.deleteById(NotificationId.of(notificationId)))
                    .doOnSuccess(v -> log.info("Notification deleted successfully: {}", notificationId))
                    .doOnError(error -> log.error("Failed to delete notification: {}", notificationId, error));
        });
    }
}
