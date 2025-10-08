package biz.ugur.busroutebackend.client.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import lombok.Getter;

import java.time.Instant;

@Getter
public class ClientAuthenticationException extends ClientDomainException {

    @Getter
    public enum AuthErrorType {
        INVALID_CREDENTIALS("Invalid email or password"),
        ACCOUNT_LOCKED("Account is locked"),
        ACCOUNT_DISABLED("Account is disabled"),
        ACCOUNT_NOT_VERIFIED("Account email not verified"),
        TOKEN_EXPIRED("Authentication token has expired"),
        TOKEN_INVALID("Invalid authentication token"),
        SESSION_EXPIRED("Session has expired");

        private final String defaultMessage;

        AuthErrorType(String defaultMessage) {
            this.defaultMessage = defaultMessage;
        }
    }

    private final AuthErrorType authErrorType;
    private final String email;
    private final Instant attemptTime;
    private final String clientIp;

    public ClientAuthenticationException(AuthErrorType authErrorType, String email) {
        super("AUTHENTICATION_ERROR", authErrorType.getDefaultMessage(), Severity.WARNING);
        this.authErrorType = authErrorType;
        this.email = email;
        this.attemptTime = Instant.now();
        this.clientIp = null;
    }

    public ClientAuthenticationException(AuthErrorType authErrorType, String email, CorrelationId correlationId) {
        super("AUTHENTICATION_ERROR", authErrorType.getDefaultMessage(), Severity.WARNING, correlationId);
        this.authErrorType = authErrorType;
        this.email = email;
        this.attemptTime = Instant.now();
        this.clientIp = null;
    }

    public ClientAuthenticationException(AuthErrorType authErrorType, String email, String clientIp, CorrelationId correlationId) {
        super("AUTHENTICATION_ERROR", authErrorType.getDefaultMessage(), Severity.ERROR, correlationId);
        this.authErrorType = authErrorType;
        this.email = email;
        this.attemptTime = Instant.now();
        this.clientIp = clientIp;
    }

    public static ClientAuthenticationException invalidCredentials(String email) {
        return new ClientAuthenticationException(AuthErrorType.INVALID_CREDENTIALS, email);
    }

    public static ClientAuthenticationException invalidCredentials(String email, CorrelationId correlationId) {
        return new ClientAuthenticationException(AuthErrorType.INVALID_CREDENTIALS, email, correlationId);
    }

    public static ClientAuthenticationException accountNotVerified(String email) {
        return new ClientAuthenticationException(AuthErrorType.ACCOUNT_NOT_VERIFIED, email);
    }

    public static ClientAuthenticationException accountNotVerified(String email, CorrelationId correlationId) {
        return new ClientAuthenticationException(AuthErrorType.ACCOUNT_NOT_VERIFIED, email, correlationId);
    }

    public boolean shouldLockAccount() {
        return authErrorType == AuthErrorType.INVALID_CREDENTIALS;
    }

    public boolean shouldAuditEvent() {
        return true;
    }
}
