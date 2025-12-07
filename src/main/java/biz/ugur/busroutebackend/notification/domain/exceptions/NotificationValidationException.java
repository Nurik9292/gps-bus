package biz.ugur.busroutebackend.notification.domain.exceptions;

import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class NotificationValidationException extends NotificationDomainException {

    private final Map<String, List<String>> fieldErrors;
    private final String failedField;

    public NotificationValidationException(String message) {
        super("VALIDATION_ERROR", message, Severity.WARNING);
        this.fieldErrors = Map.of();
        this.failedField = null;
    }

    public NotificationValidationException(String field, String message) {
        super("VALIDATION_ERROR", String.format("Validation failed for field '%s': %s", field, message), Severity.WARNING);
        this.fieldErrors = Map.of(field, List.of(message));
        this.failedField = field;
    }

    public NotificationValidationException(Map<String, List<String>> fieldErrors) {
        super("VALIDATION_ERROR", "Notification validation failed", Severity.WARNING);
        this.fieldErrors = fieldErrors;
        this.failedField = fieldErrors.keySet().stream().findFirst().orElse(null);
    }

    public boolean hasSingleFieldError() {
        return fieldErrors.size() == 1;
    }

    public static NotificationValidationException singleField(String field, String message) {
        return new NotificationValidationException(field, message);
    }
}
