package biz.ugur.busroutebackend.notification.application.usecase.admin;

import biz.ugur.busroutebackend.notification.application.dto.NotificationList;
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
public class GetAllNotificationsUseCase extends BaseUseCase<Mono<Boolean>, NotificationList> {

    private final AdminNotificationRepository notificationRepository;
    private final NotificationResponseMapper notificationResponseMapper;

    public GetAllNotificationsUseCase(AdminNotificationRepository notificationRepository,
                                      CorrelationContextService correlationContextService,
                                      EventBus eventBus,
                                      NotificationResponseMapper notificationResponseMapper) {
        super(correlationContextService, eventBus);
        this.notificationRepository = notificationRepository;
        this.notificationResponseMapper = notificationResponseMapper;
    }


    @Override
    protected Mono<NotificationList> process(Mono<Boolean> activeOnly) {
        return activeOnly.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "admin";
    }

    private Mono<NotificationList> processInternal(Boolean activeOnly) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.debug("Fetching all notifications - CorrelationId: {}, ActiveOnly: {}", correlationId, activeOnly);

            var notificationFlux = Boolean.TRUE.equals(activeOnly)
                    ? notificationRepository.findActiveNotifications()
                    : notificationRepository.findAll();

            return notificationFlux
                    .flatMap(notificationResponseMapper::toResponse)
                    .collectList()
                    .zipWith(notificationRepository.countActiveNotifications())
                    .map(tuple -> NotificationList.of(
                            tuple.getT1(),
                            tuple.getT2(),
                            1,
                            tuple.getT1().size(),
                            tuple.getT1().size()
                    ))
                    .doOnSuccess(response -> log.debug(
                            "CorrelationId: {} - Retrieved {} notifications ({} active)",
                            correlationId,
                            response.getNotifications().size(),
                            response.getActiveCount()
                    ));
        });
    }
}
