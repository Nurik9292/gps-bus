package biz.ugur.busroutebackend.notification.application.usecase.admin;

import biz.ugur.busroutebackend.notification.application.dto.NotificationResponse;
import biz.ugur.busroutebackend.notification.application.mapper.NotificationResponseMapper;
import biz.ugur.busroutebackend.notification.domain.exceptions.NotificationNotFoundException;
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
public class GetNotificationByIdUseCase extends BaseUseCase<Mono<String>, NotificationResponse> {

    private final AdminNotificationRepository notificationRepository;
    private final NotificationResponseMapper notificationResponseMapper;

    public GetNotificationByIdUseCase(AdminNotificationRepository notificationRepository,
                                      CorrelationContextService correlationService,
                                      EventBus eventBus,
                                      NotificationResponseMapper notificationResponseMapper) {
        super(correlationService, eventBus);
        this.notificationRepository = notificationRepository;
        this.notificationResponseMapper = notificationResponseMapper;
    }

    @Override
    protected Mono<NotificationResponse> process(Mono<String> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<NotificationResponse> processInternal(String notificationId) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.debug("Fetching notification by ID - CorrelationId: {}, NotificationId: {}", correlationId, notificationId);

            return notificationRepository.findById(NotificationId.of(notificationId))
                    .switchIfEmpty(Mono.error(new NotificationNotFoundException(notificationId)))
                    .flatMap(notificationResponseMapper::toResponse)
                    .doOnSuccess(response -> log.debug(
                            "CorrelationId: {} - Successfully retrieved notification with id: {}, title: {}",
                            correlationId,
                            notificationId,
                            response.title()
                    ));
        });
    }
}
