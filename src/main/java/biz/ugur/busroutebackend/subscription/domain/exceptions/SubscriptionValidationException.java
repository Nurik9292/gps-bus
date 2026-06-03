package biz.ugur.busroutebackend.subscription.domain.exceptions;

import lombok.Getter;

import java.util.Map;

@Getter
public class SubscriptionValidationException extends SubscriptionDomainException {

    private final transient Map<String, String> fieldErrors;
    private final String failedField;

    public SubscriptionValidationException(String field, String message) {
        super("VALIDATION_ERROR", "Subscription validation failed: " + field + " — " + message, Severity.WARNING);
        this.fieldErrors = Map.of(field, message);
        this.failedField = field;
    }

    public SubscriptionValidationException(Map<String, String> fieldErrors) {
        super("VALIDATION_ERROR", "Subscription validation failed: " + fieldErrors, Severity.WARNING);
        this.fieldErrors = Map.copyOf(fieldErrors);
        this.failedField = null;
    }

    public static SubscriptionValidationException singleField(String field, String message) {
        return new SubscriptionValidationException(field, message);
    }
}
