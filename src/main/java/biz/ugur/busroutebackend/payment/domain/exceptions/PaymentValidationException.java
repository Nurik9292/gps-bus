package biz.ugur.busroutebackend.payment.domain.exceptions;

import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class PaymentValidationException extends PaymentDomainException {

    private final Map<String, List<String>> fieldErrors;
    private final String failedField;

    public PaymentValidationException(String message) {
        super("VALIDATION_ERROR", message, Severity.WARNING);
        this.fieldErrors = Map.of();
        this.failedField = null;
    }

    public PaymentValidationException(String field, String message) {
        super("VALIDATION_ERROR",
                String.format("Validation failed for field '%s': %s", field, message),
                Severity.WARNING);
        this.fieldErrors = Map.of(field, List.of(message));
        this.failedField = field;
    }

    public static PaymentValidationException singleField(String field, String message) {
        return new PaymentValidationException(field, message);
    }
}
