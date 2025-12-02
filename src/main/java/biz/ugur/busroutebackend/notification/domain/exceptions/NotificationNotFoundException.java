package biz.ugur.busroutebackend.notification.domain.exceptions;


import lombok.Getter;

@Getter
public class NotificationNotFoundException extends NotificationDomainException {

    private final String notificationId;

    public NotificationNotFoundException(String notificationId) {
        super(String.format("Notification not found with ID: %s", notificationId));
        this.notificationId = notificationId;
    }

    public NotificationNotFoundException(String notificationId, Throwable cause) {
        super(String.format("Notification not found with ID: %s", notificationId), cause);
        this.notificationId = notificationId;
    }

}
