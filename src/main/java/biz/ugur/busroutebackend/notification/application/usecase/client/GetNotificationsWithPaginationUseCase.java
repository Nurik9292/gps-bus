package biz.ugur.busroutebackend.notification.application.usecase.client;

import biz.ugur.busroutebackend.notification.application.dto.NotificationList;
import biz.ugur.busroutebackend.notification.application.dto.NotificationPaginationQuery;
import biz.ugur.busroutebackend.notification.application.mapper.NotificationResponseMapper;
import biz.ugur.busroutebackend.notification.domain.model.Notification;
import biz.ugur.busroutebackend.notification.domain.repository.ClientNotificationRepository;
import biz.ugur.busroutebackend.notification.domain.specification.NotificationSpecifications;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service("clientGetNotificationsWithPaginationUseCase")
@Slf4j
public class GetNotificationsWithPaginationUseCase extends BaseUseCase<Mono<NotificationPaginationQuery>, NotificationList> {

    private final ClientNotificationRepository notificationRepository;
    private final NotificationResponseMapper notificationResponseMapper;

    public GetNotificationsWithPaginationUseCase(ClientNotificationRepository notificationRepository,
                                                 CorrelationContextService correlationContextService,
                                                 EventBus eventBus,
                                                 NotificationResponseMapper notificationResponseMapper) {
        super(correlationContextService, eventBus);
        this.notificationRepository = notificationRepository;
        this.notificationResponseMapper = notificationResponseMapper;
    }


    @Override
    protected Mono<NotificationList> process(Mono<NotificationPaginationQuery> query) {
        return query.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "client";
    }

    private Mono<NotificationList> processInternal(NotificationPaginationQuery query) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.debug("Fetching notifications with pagination CorrelationId: {} - client: page={}, size={}",
                    correlationId, query.getPage(), query.getSize());

            Pageable pageable = createPageable(query);
            Specification<Notification> specification = NotificationSpecifications.isActive();

            Mono<List<Notification>> notificationsMono = notificationRepository.findActiveNotificationsWithPagination(pageable)
                    .collectList();

            Mono<Long> totalCountMono = notificationRepository.countActiveNotifications();

            return Mono.zip(notificationsMono, totalCountMono)
                    .flatMap(tuple -> {
                        List<Notification> notifications = tuple.getT1();
                        Long totalCount = tuple.getT2();

                        return Flux.fromIterable(notifications)
                                .flatMap(notificationResponseMapper::toResponse)
                                .collectList()
                                .map(notificationResponses -> NotificationList.of(
                                        notificationResponses,
                                        totalCount,
                                        query.getPage(),
                                        query.getSize(),
                                        totalCount
                                ));
                    })
                    .doOnSuccess(response -> log.debug(
                            "CorrelationId: {} - Retrieved {} active notifications out of {} total",
                            correlationId,
                            response.getNotifications().size(),
                            response.getPagination().getTotalItems()
                    ));
        });
    }

    private Pageable createPageable(NotificationPaginationQuery query) {
        Sort sort = Sort.by(
                query.getSortOrder().equalsIgnoreCase("desc") ?
                        Sort.Direction.DESC : Sort.Direction.ASC,
                query.getSortField()
        );

        return PageRequest.of(query.getPage() - 1, query.getSize(), sort);
    }
}
