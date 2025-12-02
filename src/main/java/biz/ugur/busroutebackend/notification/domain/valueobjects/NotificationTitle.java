package biz.ugur.busroutebackend.notification.domain.valueobjects;

import biz.ugur.busroutebackend.notification.domain.exceptions.NotificationValidationException;
import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;


@Getter
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = false)
public class NotificationTitle extends ValueObject {

    private final String value;

    private NotificationTitle(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new NotificationValidationException("Notification title cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static NotificationTitle of(String value) {
        return new NotificationTitle(value);
    }

}
