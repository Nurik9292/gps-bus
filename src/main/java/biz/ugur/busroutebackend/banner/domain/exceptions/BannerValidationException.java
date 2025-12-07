package biz.ugur.busroutebackend.banner.domain.exceptions;

import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class BannerValidationException extends BannerDomainException {

    private final Map<String, List<String>> fieldErrors;
    private final String failedField;

    public BannerValidationException(String message) {
        super("VALIDATION_ERROR", message, Severity.WARNING);
        this.fieldErrors = Map.of();
        this.failedField = null;
    }

    public BannerValidationException(String field, String message) {
        super("VALIDATION_ERROR", String.format("Validation failed for field '%s': %s", field, message), Severity.WARNING);
        this.fieldErrors = Map.of(field, List.of(message));
        this.failedField = field;
    }

    public BannerValidationException(Map<String, List<String>> fieldErrors) {
        super("VALIDATION_ERROR", "Banner validation failed", Severity.WARNING);
        this.fieldErrors = fieldErrors;
        this.failedField = fieldErrors.keySet().stream().findFirst().orElse(null);
    }

    public boolean hasSingleFieldError() {
        return fieldErrors.size() == 1;
    }

    public static BannerValidationException singleField(String field, String message) {
        return new BannerValidationException(field, message);
    }
}
