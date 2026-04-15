package biz.ugur.busroutebackend.business.domain.exceptions;

import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class BusinessValidationException extends BusinessDomainException {

    private final Map<String, List<String>> fieldErrors;
    private final String failedField;

    public BusinessValidationException(String message) {
        super("VALIDATION_ERROR", message, Severity.WARNING);
        this.fieldErrors = Map.of();
        this.failedField = null;
    }

    public BusinessValidationException(String field, String message) {
        super("VALIDATION_ERROR",
                String.format("Validation failed for field '%s': %s", field, message),
                Severity.WARNING);
        this.fieldErrors = Map.of(field, List.of(message));
        this.failedField = field;
    }

    public BusinessValidationException(Map<String, List<String>> fieldErrors) {
        super("VALIDATION_ERROR", "Business validation failed", Severity.WARNING);
        this.fieldErrors = fieldErrors;
        this.failedField = fieldErrors.keySet().stream().findFirst().orElse(null);
    }

    public static BusinessValidationException singleField(String field, String message) {
        return new BusinessValidationException(field, message);
    }
}
