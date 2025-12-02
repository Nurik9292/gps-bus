package biz.ugur.busroutebackend.notification.domain.model;

import biz.ugur.busroutebackend.notification.domain.events.*;
import biz.ugur.busroutebackend.notification.domain.exceptions.NotificationValidationException;
import biz.ugur.busroutebackend.notification.domain.valueobjects.NotificationId;
import biz.ugur.busroutebackend.notification.domain.valueobjects.NotificationTitle;
import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Builder(toBuilder = true)
@Getter
@EqualsAndHashCode(callSuper = false)
public class Notification extends AggregateRoot<Notification, NotificationId> {

    private final NotificationId id;
    private final NotificationTitle title;
    private final String content;
    private final Boolean isActive;
    private final Integer displayOrder;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    public static Notification create(NotificationTitle title,
                                      Integer displayOrder,
                                      String content,
                                      Boolean isActive) {

        Notification notification = builder()
                .id(NotificationId.generate())
                .title(title)
                .isActive(isActive != null ? isActive : true)
                .displayOrder(displayOrder != null ? displayOrder : 0)
                .content(content)
                .version(0L)
                .build();

        notification.registerEvent(new NotificationCreatedEvent(
                notification.id.getValue(),
                notification.title.getValue(),
                notification.displayOrder,
                notification.content
        ));

        return notification;
    }

    public static Notification restore(NotificationId id,
                                       NotificationTitle title,
                                       Boolean isActive,
                                       Integer displayOrder,
                                       String content,
                                       LocalDateTime createdAt,
                                       LocalDateTime updatedAt,
                                       Long version) {

        return builder()
                .id(id)
                .title(title)
                .isActive(isActive)
                .displayOrder(displayOrder)
                .content(content)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version != null ? version : 0L)
                .build();
    }


    public Notification updateNotification(
            NotificationTitle title,
            Integer displayOrder,
            String content) {

        if (title == null) {
            throw new NotificationValidationException("Title cannot be null");
        }

        Map<String, Object> changes = new HashMap<>();

        if (!this.title.equals(title)) {
            changes.put("title", title.getValue());
        }

        Integer finalDisplayOrder = displayOrder != null ? displayOrder : this.displayOrder;
        if (!finalDisplayOrder.equals(this.displayOrder)) {
            changes.put("displayOrder", finalDisplayOrder);
        }

        String normalizedContent = content != null ? content.trim() : this.content;
        if (!java.util.Objects.equals(normalizedContent, this.content)) {
            changes.put("content", normalizedContent);
        }

        Notification updatedNotification = this.toBuilder()
                .title(title)
                .displayOrder(finalDisplayOrder)
                .content(normalizedContent)
                .build();

        if (!changes.isEmpty()) {
            updatedNotification.registerEvent(new NotificationUpdatedEvent(this.id.getValue(), changes));
        }

        return updatedNotification;
    }


    public Notification deactivate() {
        if (Boolean.FALSE.equals(this.isActive)) {
            return this;
        }

        Notification deactivatedNotification = this.toBuilder()
                .isActive(false)
                .build();

        deactivatedNotification.registerEvent(new NotificationDeactivatedEvent(this.id.getValue()));
        return deactivatedNotification;
    }

    public Notification activate() {
        if (Boolean.TRUE.equals(this.isActive)) {
            return this;
        }

        Notification activatedNotification = this.toBuilder()
                .isActive(true)
                .build();

        activatedNotification.registerEvent(new NotificationActivatedEvent(this.id.getValue()));
        return activatedNotification;
    }

    @Override
    public NotificationId getId() {
        return id;
    }

    @Override
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public Long getVersion() {
        return version;
    }

    @Override
    public void setVersion(Long version) {
        this.version = version;
    }

}
