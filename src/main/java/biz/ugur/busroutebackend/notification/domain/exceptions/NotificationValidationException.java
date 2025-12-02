package biz.ugur.busroutebackend.notification.domain.exceptions;


public class NotificationValidationException extends NotificationDomainException {

    public NotificationValidationException(String message) {
        super(message);
    }

    public NotificationValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
