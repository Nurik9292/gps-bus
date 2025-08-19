package biz.ugur.busroutebackend.admin.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class AdminPasswordException extends AdminDomainException {

    @Getter
    public enum PasswordErrorType {
        WEAK_PASSWORD("Password does not meet security requirements"),
        INCORRECT_PASSWORD("Current password is incorrect"),
        PASSWORD_EXPIRED("Password has expired and must be changed"),
        PASSWORD_REUSED("Password was recently used and cannot be reused"),
        PASSWORD_MISMATCH("Password confirmation does not match"),
        PASSWORD_LOCKED("Account is locked due to too many failed attempts");

        private final String defaultMessage;

        PasswordErrorType(String defaultMessage) {
            this.defaultMessage = defaultMessage;
        }

    }

    private final PasswordErrorType errorType;
    private final String username;
    private final Map<String, List<String>> validationErrors;

    public AdminPasswordException(PasswordErrorType errorType, String username) {
        super("PASSWORD_ERROR", errorType.getDefaultMessage(), Severity.WARNING);
        this.errorType = errorType;
        this.username = username;
        this.validationErrors = Map.of();
    }

    public AdminPasswordException(PasswordErrorType errorType, String username, String customMessage) {
        super("PASSWORD_ERROR", customMessage, Severity.WARNING);
        this.errorType = errorType;
        this.username = username;
        this.validationErrors = Map.of();
    }

    public AdminPasswordException(String username, Map<String, List<String>> validationErrors) {
        super("PASSWORD_VALIDATION", "Password validation failed", Severity.WARNING);
        this.errorType = PasswordErrorType.WEAK_PASSWORD;
        this.username = username;
        this.validationErrors = validationErrors;
    }

    public AdminPasswordException(PasswordErrorType errorType, String username, CorrelationId correlationId) {
        super("PASSWORD_ERROR", errorType.getDefaultMessage(), Severity.ERROR, correlationId);
        this.errorType = errorType;
        this.username = username;
        this.validationErrors = Map.of();
    }

    public boolean isAuthenticationFailure() {
        return errorType == PasswordErrorType.INCORRECT_PASSWORD ||
                errorType == PasswordErrorType.PASSWORD_LOCKED;
    }
}