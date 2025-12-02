package biz.ugur.busroutebackend.notification.application.usecase.admin;

import biz.ugur.busroutebackend.notification.application.dto.CreateNotificationCommand;
import biz.ugur.busroutebackend.notification.application.dto.NotificationResponse;
import biz.ugur.busroutebackend.notification.application.factory.NotificationFactory;
import biz.ugur.busroutebackend.notification.application.mapper.NotificationResponseMapper;
import biz.ugur.busroutebackend.notification.domain.repository.AdminNotificationRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CreateNotificationUseCase extends BaseUseCase<Mono<CreateNotificationCommand>, NotificationResponse> {

    private final AdminNotificationRepository notificationRepository;
    private final NotificationFactory notificationFactory;
    private final NotificationResponseMapper notificationResponseMapper;

    public CreateNotificationUseCase(AdminNotificationRepository notificationRepository,
                                     CorrelationContextService correlationService,
                                     EventBus eventBus,
                                     NotificationFactory notificationFactory,
                                     NotificationResponseMapper notificationResponseMapper) {
        super(correlationService, eventBus);
        this.notificationRepository = notificationRepository;
        this.notificationFactory = notificationFactory;
        this.notificationResponseMapper = notificationResponseMapper;
    }


    @Override
    protected Mono<NotificationResponse> process(Mono<CreateNotificationCommand> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<NotificationResponse> processInternal(CreateNotificationCommand command) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Creating new notification: CorrelationId - {} NotificationTitle  {}", correlationId, command.title());

            return notificationFactory.create(command)
                    .flatMap(notificationRepository::save)
                    .flatMap(notificationResponseMapper::toResponse)
                    .doOnSuccess(response -> log.info("Notification created successfully: {}", response.title()))
                    .doOnError(error -> log.error("Failed to create notification: {}", command.title(), error));
        });
    }
}
