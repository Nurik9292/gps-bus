package biz.ugur.busroutebackend.admin.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.CorrelationId;
import lombok.Getter;

import java.util.List;
import java.util.Map;


@Getter
public class AdminValidationException extends AdminDomainException {

    private final Map<String, List<String>> fieldErrors;
    private final String failedField;

    public AdminValidationException(String field, String error) {
        super("VALIDATION_ERROR",
                String.format("Validation failed for field '%s': %s", field, error));
        this.fieldErrors = Map.of(field, List.of(error));
        this.failedField = field;
    }

    public AdminValidationException(Map<String, List<String>> fieldErrors) {
        super("VALIDATION_ERROR", "Multiple validation errors occurred");
        this.fieldErrors = fieldErrors;
        this.failedField = null;
    }

    public AdminValidationException(String field, String error, CorrelationId correlationId) {
        super("VALIDATION_ERROR",
                String.format("Validation failed for field '%s': %s", field, error),
                Severity.ERROR,
                correlationId);
        this.fieldErrors = Map.of(field, List.of(error));
        this.failedField = field;
    }

    public boolean hasSingleFieldError() {
        return fieldErrors.size() == 1 && failedField != null;
    }
}