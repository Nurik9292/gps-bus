package biz.ugur.busroutebackend.notification.domain.repository;

import biz.ugur.busroutebackend.notification.domain.model.Notification;
import biz.ugur.busroutebackend.notification.domain.valueobjects.NotificationId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface AdminNotificationRepository extends BaseRepository<Notification, NotificationId> {

    Flux<Notification> findActiveNotifications();

    Mono<Long> countActiveNotifications();

    Flux<Notification> findBySpecification(Specification<Notification> specification);

    Flux<Notification> findBySpecification(Specification<Notification> specification, Pageable pageable);

    Mono<Long> countBySpecification(Specification<Notification> specification);
}
