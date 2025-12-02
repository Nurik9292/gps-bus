package biz.ugur.busroutebackend.notification.infrastructure.mapper;

import biz.ugur.busroutebackend.notification.domain.model.Notification;
import biz.ugur.busroutebackend.notification.domain.valueobjects.NotificationId;
import biz.ugur.busroutebackend.notification.domain.valueobjects.NotificationTitle;
import biz.ugur.busroutebackend.notification.infrastructure.persistence.entity.NotificationEntity;

public class NotificationMapper {

    private NotificationMapper() {}

    public static Notification toDomain(NotificationEntity entity) {
        return Notification.restore(
                NotificationId.of(entity.getId()),
                NotificationTitle.of(entity.getTitle()),
                entity.getIsActive(),
                entity.getDisplayOrder(),
                entity.getContent(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    public static NotificationEntity toEntity(Notification notification) {
        return NotificationEntity.builder()
                .id(notification.getId().getValue())
                .title(notification.getTitle().getValue())
                .content(notification.getContent())
                .isActive(notification.getIsActive())
                .displayOrder(notification.getDisplayOrder())
                .createdAt(notification.getCreatedAt())
                .updatedAt(notification.getUpdatedAt())
                .version(notification.getVersion())
                .build();
    }
}
