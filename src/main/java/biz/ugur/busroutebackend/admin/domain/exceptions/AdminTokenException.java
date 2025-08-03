package biz.ugur.busroutebackend.admin.domain.exceptions;

import biz.ugur.busroutebackend.shared.domain.CorrelationId;
import lombok.Getter;

import java.time.Instant;

@Getter
public class AdminTokenException extends AdminDomainException {

    @Getter
    public enum TokenErrorType {
        INVALID_TOKEN("Invalid or malformed token"),
        EXPIRED_TOKEN("Token has expired"),
        BLACKLISTED_TOKEN("Token has been revoked"),
        INVALID_TOKEN_TYPE("Invalid token type for operation"),
        MISSING_TOKEN("Authentication token is required"),
        ADMIN_NOT_FOUND("Admin associated with token not found"),
        ADMIN_DISABLED("Admin account is disabled"),
        TOKEN_GENERATION_FAILED("Failed to generate new token"),
        REFRESH_TOKEN_INVALID("Refresh token is invalid or expired");

        private final String defaultMessage;

        TokenErrorType(String defaultMessage) {
            this.defaultMessage = defaultMessage;
        }

    }

    private final TokenErrorType tokenErrorType;
    private final String tokenValue;
    private final String adminUsername;
    private final Instant tokenExpiry;

    public AdminTokenException(TokenErrorType tokenErrorType) {
        super("TOKEN_ERROR", tokenErrorType.getDefaultMessage(), Severity.WARNING);
        this.tokenErrorType = tokenErrorType;
        this.tokenValue = null;
        this.adminUsername = null;
        this.tokenExpiry = null;
    }

    public AdminTokenException(TokenErrorType tokenErrorType, String customMessage, CorrelationId correlationId) {
        super("TOKEN_ERROR", customMessage, Severity.WARNING, correlationId);
        this.tokenErrorType = tokenErrorType;
        this.tokenValue = null;
        this.adminUsername = null;
        this.tokenExpiry = null;
    }


    public AdminTokenException(TokenErrorType tokenErrorType, String tokenValue,
                               String adminUsername, CorrelationId correlationId) {
        super("TOKEN_ERROR", tokenErrorType.getDefaultMessage(), Severity.ERROR, correlationId);
        this.tokenErrorType = tokenErrorType;
        this.tokenValue = tokenValue != null && tokenValue.length() > 8 ?
                tokenValue.substring(0, 8) + "..." : tokenValue;
        this.adminUsername = adminUsername;
        this.tokenExpiry = null;
    }

    public AdminTokenException(TokenErrorType tokenErrorType, String tokenValue,
                               Instant tokenExpiry, CorrelationId correlationId) {
        super("TOKEN_ERROR",
                String.format("%s (expired at %s)", tokenErrorType.getDefaultMessage(), tokenExpiry),
                Severity.ERROR,
                correlationId);
        this.tokenErrorType = tokenErrorType;
        this.tokenValue = tokenValue != null && tokenValue.length() > 8 ?
                tokenValue.substring(0, 8) + "..." : tokenValue;
        this.adminUsername = null;
        this.tokenExpiry = tokenExpiry;
    }

    public static AdminTokenException invalidToken(String token, CorrelationId correlationId) {
        return new AdminTokenException(TokenErrorType.INVALID_TOKEN, token, (String) null, correlationId);
    }

    public static AdminTokenException expiredToken(String token, Instant expiry, CorrelationId correlationId) {
        return new AdminTokenException(TokenErrorType.EXPIRED_TOKEN, token, expiry, correlationId);
    }

    public static AdminTokenException blacklistedToken(String token, CorrelationId correlationId) {
        return new AdminTokenException(TokenErrorType.BLACKLISTED_TOKEN, token, (String) null, correlationId);
    }

    public static AdminTokenException invalidTokenType(String expectedType, CorrelationId correlationId) {
        return new AdminTokenException(TokenErrorType.INVALID_TOKEN_TYPE,
                "Expected token type: " + expectedType, correlationId);
    }

    public static AdminTokenException adminNotFound(String username, CorrelationId correlationId) {
        return new AdminTokenException(TokenErrorType.ADMIN_NOT_FOUND, null, username, correlationId);
    }

    public static AdminTokenException adminDisabled(String username, CorrelationId correlationId) {
        return new AdminTokenException(TokenErrorType.ADMIN_DISABLED, null, username, correlationId);
    }

    public boolean isRetryable() {
        return tokenErrorType == TokenErrorType.TOKEN_GENERATION_FAILED;
    }

    public boolean requiresReAuthentication() {
        return tokenErrorType == TokenErrorType.EXPIRED_TOKEN ||
                tokenErrorType == TokenErrorType.BLACKLISTED_TOKEN ||
                tokenErrorType == TokenErrorType.INVALID_TOKEN;
    }
}