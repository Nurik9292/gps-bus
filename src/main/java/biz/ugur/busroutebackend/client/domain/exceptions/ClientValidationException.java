package biz.ugur.busroutebackend.client.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import lombok.Getter;

import java.util.*;


@Getter
public class ClientValidationException extends ClientDomainException {

    private final Map<String, List<String>> fieldErrors;

    public ClientValidationException(Map<String, List<String>> fieldErrors) {
        super("VALIDATION_ERROR", "Client validation failed", Severity.WARNING);
        this.fieldErrors = new LinkedHashMap<>(fieldErrors);
    }

    public ClientValidationException(Map<String, List<String>> fieldErrors, CorrelationId correlationId) {
        super("VALIDATION_ERROR", "Client validation failed", Severity.WARNING, correlationId);
        this.fieldErrors = new LinkedHashMap<>(fieldErrors);
    }

    public ClientValidationException(String field, String error) {
        super("VALIDATION_ERROR", String.format("Validation failed for field '%s': %s", field, error), Severity.WARNING);
        this.fieldErrors = new LinkedHashMap<>();
        this.fieldErrors.put(field, List.of(error));
    }

    public ClientValidationException(String field, String error, CorrelationId correlationId) {
        super("VALIDATION_ERROR", String.format("Validation failed for field '%s': %s", field, error), Severity.WARNING, correlationId);
        this.fieldErrors = new LinkedHashMap<>();
        this.fieldErrors.put(field, List.of(error));
    }

    public boolean hasSingleFieldError() {
        return fieldErrors.size() == 1;
    }

    public String getFailedField() {
        return hasSingleFieldError() ? fieldErrors.keySet().iterator().next() : null;
    }

    public static ClientValidationException singleField(String field, String error) {
        return new ClientValidationException(field, error);
    }

    public static ClientValidationException singleField(String field, String error, CorrelationId correlationId) {
        return new ClientValidationException(field, error, correlationId);
    }
}
