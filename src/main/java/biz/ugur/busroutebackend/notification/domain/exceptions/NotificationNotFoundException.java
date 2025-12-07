package biz.ugur.busroutebackend.notification.domain.exceptions;

import lombok.Getter;

@Getter
public class NotificationNotFoundException extends NotificationDomainException {

    private final String notificationId;

    public NotificationNotFoundException(String notificationId) {
        super("NOT_FOUND", String.format("Notification not found with ID: %s", notificationId), Severity.WARNING);
        this.notificationId = notificationId;
    }

    public static NotificationNotFoundException byId(String notificationId) {
        return new NotificationNotFoundException(notificationId);
    }
}
