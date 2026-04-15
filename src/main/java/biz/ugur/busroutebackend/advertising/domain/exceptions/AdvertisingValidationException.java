package biz.ugur.busroutebackend.advertising.domain.exceptions;

import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class AdvertisingValidationException extends AdvertisingDomainException {

    private final Map<String, List<String>> fieldErrors;
    private final String failedField;

    public AdvertisingValidationException(String message) {
        super("VALIDATION_ERROR", message, Severity.WARNING);
        this.fieldErrors = Map.of();
        this.failedField = null;
    }

    public AdvertisingValidationException(String field, String message) {
        super("VALIDATION_ERROR",
                String.format("Validation failed for field '%s': %s", field, message),
                Severity.WARNING);
        this.fieldErrors = Map.of(field, List.of(message));
        this.failedField = field;
    }

    public static AdvertisingValidationException singleField(String field, String message) {
        return new AdvertisingValidationException(field, message);
    }
}
