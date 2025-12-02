package biz.ugur.busroutebackend.notification.application.usecase.admin;

import biz.ugur.busroutebackend.notification.application.dto.UpdateNotificationCommand;
import biz.ugur.busroutebackend.notification.application.dto.NotificationResponse;
import biz.ugur.busroutebackend.notification.application.factory.NotificationFactory;
import biz.ugur.busroutebackend.notification.application.mapper.NotificationResponseMapper;
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
public class UpdateNotificationUseCase extends BaseUseCase<Mono<UpdateNotificationCommand>, NotificationResponse> {

    private final AdminNotificationRepository notificationRepository;
    private final NotificationFactory notificationFactory;
    private final NotificationResponseMapper notificationResponseMapper;

    public UpdateNotificationUseCase(AdminNotificationRepository notificationRepository,
                                     EventBus eventBus,
                                     CorrelationContextService correlationContextService,
                                     NotificationFactory notificationFactory,
                                     NotificationResponseMapper notificationResponseMapper) {
        super(correlationContextService, eventBus);
        this.notificationRepository = notificationRepository;
        this.notificationFactory = notificationFactory;
        this.notificationResponseMapper = notificationResponseMapper;
    }


    @Override
    protected Mono<NotificationResponse> process(Mono<UpdateNotificationCommand> command) {
        return command.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<NotificationResponse> processInternal(UpdateNotificationCommand command) {
        return correlationService.getCurrentCorrelationId()
                .doOnNext(correlationId -> log.info(
                        "Updating notification: CorrelationId - {} NotificationId - {}", correlationId, command.id()
                ))
                .then(notificationRepository.findById(NotificationId.of(command.id())))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Notification not found: " + command.id())))
                .flatMap(notification -> notificationFactory.update(notification, command))
                .flatMap(notificationRepository::save)
                .flatMap(notificationResponseMapper::toResponse)
                .doOnSuccess(response -> log.info("Notification updated successfully: {}", response.title()))
                .doOnError(error -> log.error("Failed to update notification: {}", command.id(), error));
    }

}
