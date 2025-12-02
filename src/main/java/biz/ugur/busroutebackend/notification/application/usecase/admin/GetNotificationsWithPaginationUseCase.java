package biz.ugur.busroutebackend.notification.application.usecase.admin;

import biz.ugur.busroutebackend.notification.application.dto.NotificationList;
import biz.ugur.busroutebackend.notification.application.dto.NotificationPaginationQuery;
import biz.ugur.busroutebackend.notification.application.mapper.NotificationResponseMapper;
import biz.ugur.busroutebackend.notification.domain.model.Notification;
import biz.ugur.busroutebackend.notification.domain.repository.AdminNotificationRepository;
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

@Service
@Slf4j
public class GetNotificationsWithPaginationUseCase extends BaseUseCase<Mono<NotificationPaginationQuery>, NotificationList> {

    private final AdminNotificationRepository notificationRepository;
    private final NotificationResponseMapper notificationResponseMapper;

    public GetNotificationsWithPaginationUseCase(AdminNotificationRepository notificationRepository,
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
        return "admin";
    }

    private Mono<NotificationList> processInternal(NotificationPaginationQuery query) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.debug("Fetching notifications with pagination CorrelationId: {} - admin: page={}, size={}, sort={}, order={}, active={}",
                    correlationId, query.getPage(), query.getSize(), query.getSortField(), query.getSortOrder(), query.getActiveOnly());

            Pageable pageable = createPageable(query);
            Specification<Notification> specification = buildSpecification(query);

            Mono<List<Notification>> notificationsMono = (specification != null)
                    ? notificationRepository.findBySpecification(specification, pageable).collectList()
                    : notificationRepository.findAll(pageable).collectList();

            Mono<Long> totalCountMono = (specification != null)
                    ? notificationRepository.countBySpecification(specification)
                    : notificationRepository.count();

            Mono<Long> activeCountMono = notificationRepository.countActiveNotifications();

            return Mono.zip(notificationsMono, activeCountMono, totalCountMono)
                    .flatMap(tuple -> {
                        List<Notification> notifications = tuple.getT1();
                        Long activeCount = tuple.getT2();
                        Long totalCount = tuple.getT3();

                        return Flux.fromIterable(notifications)
                                .flatMap(notificationResponseMapper::toResponse)
                                .collectList()
                                .map(notificationResponses -> NotificationList.of(
                                        notificationResponses,
                                        activeCount,
                                        query.getPage(),
                                        query.getSize(),
                                        totalCount
                                ));
                    })
                    .doOnSuccess(response -> log.debug(
                            "CorrelationId: {} - Retrieved {} notifications out of {} total ({} active)",
                            correlationId,
                            response.getNotifications().size(),
                            response.getPagination().getTotalItems(),
                            response.getActiveCount()
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


    private Specification<Notification> buildSpecification(NotificationPaginationQuery query) {
        Specification<Notification> spec = null;

        if (query.getQuery() != null && !query.getQuery().isBlank()) {
            spec = NotificationSpecifications.titleContains(query.getQuery());
        }

        if (Boolean.TRUE.equals(query.getActiveOnly())) {
            Specification<Notification> activeSpec = NotificationSpecifications.isReadyForDisplay();
            spec = (spec == null) ? activeSpec : spec.and(activeSpec);
        }

        return spec;
    }

}
