package biz.ugur.busroutebackend.admin.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.CorrelationId;
import lombok.Getter;

import java.time.Instant;

@Getter
public class AdminAuthenticationException extends AdminDomainException {

    @Getter
    public enum AuthErrorType {
        INVALID_CREDENTIALS("Invalid username or password"),
        ACCOUNT_LOCKED("Account is locked"),
        ACCOUNT_DISABLED("Account is disabled"),
        TOKEN_EXPIRED("Authentication token has expired"),
        REFRESH_TOKEN_EXPIRED("Authentication refresh token has expired"),
        TOKEN_INVALID("Invalid authentication token"),
        REFRESH_TOKEN_INVALID("Invalid authentication refresh token"),
        SESSION_EXPIRED("Session has expired"),
        TWO_FACTOR_REQUIRED("Two-factor authentication required");

        private final String defaultMessage;

        AuthErrorType(String defaultMessage) {
            this.defaultMessage = defaultMessage;
        }

    }

    private final AuthErrorType authErrorType;
    private final String username;
    private final Instant attemptTime;
    private final String clientIp;

    public AdminAuthenticationException(AuthErrorType authErrorType, String username) {
        super("AUTHENTICATION_FAILED", authErrorType.getDefaultMessage(), Severity.WARNING);
        this.authErrorType = authErrorType;
        this.username = username;
        this.attemptTime = Instant.now();
        this.clientIp = null;
    }

    public AdminAuthenticationException(AuthErrorType authErrorType, String username, CorrelationId correlationId) {
        super("AUTHENTICATION_FAILED", authErrorType.getDefaultMessage(), Severity.WARNING,  correlationId);
        this.authErrorType = authErrorType;
        this.username = username;
        this.attemptTime = Instant.now();
        this.clientIp = null;
    }

    public AdminAuthenticationException(AuthErrorType authErrorType, String username, String clientIp) {
        super("AUTHENTICATION_FAILED", authErrorType.getDefaultMessage(), Severity.WARNING);
        this.authErrorType = authErrorType;
        this.username = username;
        this.attemptTime = Instant.now();
        this.clientIp = clientIp;
    }

    public AdminAuthenticationException(AuthErrorType authErrorType, String username,
                                        String clientIp, CorrelationId correlationId) {
        super("AUTHENTICATION_FAILED", authErrorType.getDefaultMessage(), Severity.ERROR, correlationId);
        this.authErrorType = authErrorType;
        this.username = username;
        this.attemptTime = Instant.now();
        this.clientIp = clientIp;
    }

    public boolean shouldLockAccount() {
        return authErrorType == AuthErrorType.INVALID_CREDENTIALS;
    }

    public boolean shouldAuditEvent() {
        return true;
    }
}