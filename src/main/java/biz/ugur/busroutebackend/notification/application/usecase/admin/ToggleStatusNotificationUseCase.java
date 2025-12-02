package biz.ugur.busroutebackend.notification.application.usecase.admin;

import biz.ugur.busroutebackend.notification.application.dto.NotificationResponse;
import biz.ugur.busroutebackend.notification.application.mapper.NotificationResponseMapper;
import biz.ugur.busroutebackend.notification.domain.model.Notification;
import biz.ugur.busroutebackend.notification.domain.repository.AdminNotificationRepository;
import biz.ugur.busroutebackend.notification.domain.valueobjects.NotificationId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Log4j2
@Service
public class ToggleStatusNotificationUseCase extends BaseUseCase<Mono<ToggleStatusNotificationUseCase.Request>, NotificationResponse> {

    private static final String NOTIFICATION_NOT_FOUND_MSG = "Notification not found: ";

    private final AdminNotificationRepository notificationRepository;
    private final NotificationResponseMapper notificationResponseMapper;

    public ToggleStatusNotificationUseCase(CorrelationContextService correlationContextService,
                                           EventBus eventBus,
                                           AdminNotificationRepository notificationRepository,
                                           NotificationResponseMapper notificationResponseMapper) {
        super(correlationContextService, eventBus);
        this.notificationRepository = notificationRepository;
        this.notificationResponseMapper = notificationResponseMapper;
    }



    @Override
    protected Mono<NotificationResponse> process(Mono<Request> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<NotificationResponse> processInternal(Request request) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    log.info("Changing notification status CorrelationId: {} - NotificationId: {} - NewStatus: {}",
                            correlationId, request.id, request.active);

                    return notificationRepository.findById(NotificationId.of(request.id))
                            .switchIfEmpty(Mono.error(() -> new IllegalArgumentException(NOTIFICATION_NOT_FOUND_MSG + request.id)))
                            .flatMap(notification -> updateNotification(notification, request.active))
                            .flatMap(notificationResponseMapper::toResponse)
                            .doOnSuccess(n -> log.info("Notification status updated | NotificationId={} | Active={}", request.id(), n.isActive()));
                });
    }

    private Mono<Notification> updateNotification(Notification notification, Boolean active) {
        Notification updatedNotification = Objects.requireNonNullElse(active, false)
                ? notification.activate()
                : notification.deactivate();
        return notificationRepository.save(updatedNotification);
    }

    public record Request(String id, Boolean active) {}
}
