package biz.ugur.busroutebackend.notification.domain.valueobjects;

import biz.ugur.busroutebackend.notification.domain.exceptions.NotificationValidationException;
import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class NotificationId extends ValueObject {

    private final String value;

    public NotificationId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new NotificationValidationException("Notification ID cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static NotificationId generate() {
        return new NotificationId(UUID.randomUUID().toString());
    }

    public static NotificationId of(String value) {
        return new NotificationId(value);
    }

}
