package biz.ugur.busroutebackend.shared.infrastructure.security;

import biz.ugur.busroutebackend.shared.domain.exception.AbstractDomainException;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import lombok.Getter;

import java.time.Instant;

@Getter
public class JwtTokenException extends AbstractDomainException {

    private final JwtErrorType jwtErrorType;
    private String tokenValue;
    private final Instant tokenExpiry;
    private final String expectedTokenType;
    private final String actualTokenType;
    private final Throwable rootCause;

    @Getter
    public enum JwtErrorType {
        EXPIRED_TOKEN("JWT token has expired"),
        MALFORMED_TOKEN("JWT token is malformed"),
        UNSUPPORTED_TOKEN("JWT token format is not supported"),
        INVALID_SIGNATURE("JWT token signature is invalid"),
        INVALID_TOKEN("JWT token is invalid"),
        CLAIMS_PARSING_ERROR("Failed to parse JWT claims"),
        TOKEN_GENERATION_ERROR("Failed to generate JWT token"),
        ROLES_SERIALIZATION_ERROR("Failed to serialize user roles"),
        INVALID_TOKEN_TYPE("Invalid token type for operation"),
        MISSING_CLAIMS("Required claims are missing from token");

        private final String defaultMessage;

        JwtErrorType(String defaultMessage) {
            this.defaultMessage = defaultMessage;
        }

    }

    public JwtTokenException(JwtErrorType jwtErrorType) {
        super("JWT_ERROR", jwtErrorType.getDefaultMessage(), Severity.WARNING);
        this.jwtErrorType = jwtErrorType;
        this.tokenValue = null;
        this.tokenExpiry = null;
        this.expectedTokenType = null;
        this.actualTokenType = null;
        this.rootCause = null;
    }

    public JwtTokenException(JwtErrorType jwtErrorType, String customMessage) {
        super("JWT_ERROR", customMessage, Severity.WARNING);
        this.jwtErrorType = jwtErrorType;
        this.tokenValue = null;
        this.tokenExpiry = null;
        this.expectedTokenType = null;
        this.actualTokenType = null;
        this.rootCause = null;
    }

    public JwtTokenException(JwtErrorType jwtErrorType, String customMessage, Throwable cause) {
        super("JWT_ERROR", customMessage, Severity.WARNING);
        this.jwtErrorType = jwtErrorType;
        this.tokenValue = null;
        this.tokenExpiry = null;
        this.expectedTokenType = null;
        this.actualTokenType = null;
        this.rootCause = cause;
    }

    public JwtTokenException(JwtErrorType jwtErrorType, String tokenValue,
                             Instant tokenExpiry, CorrelationId correlationId) {
        super("JWT_ERROR",
                String.format("%s (expired at %s)", jwtErrorType.getDefaultMessage(), tokenExpiry),
                Severity.ERROR,
                correlationId);
        this.jwtErrorType = jwtErrorType;
        this.tokenValue = sanitizeToken(tokenValue);
        this.tokenExpiry = tokenExpiry;
        this.expectedTokenType = null;
        this.actualTokenType = null;
        this.rootCause = null;
    }

    public JwtTokenException(JwtErrorType jwtErrorType, String expectedType,
                             String actualType, CorrelationId correlationId) {
        super("JWT_ERROR",
                String.format("Expected token type '%s', but got '%s'", expectedType, actualType),
                Severity.ERROR,
                correlationId);
        this.jwtErrorType = jwtErrorType;
        this.tokenValue = null;
        this.tokenExpiry = null;
        this.expectedTokenType = expectedType;
        this.actualTokenType = actualType;
        this.rootCause = null;
    }

    public static JwtTokenException fromExpiredJwtException(ExpiredJwtException ex, String token) {
        Instant expiry = ex.getClaims() != null ?
                ex.getClaims().getExpiration().toInstant() :
                Instant.now().minusSeconds(3600);
        return new JwtTokenException(JwtErrorType.EXPIRED_TOKEN, token, expiry, CorrelationId.generate());
    }

    public static JwtTokenException fromMalformedJwtException(MalformedJwtException ex, String token) {
        JwtTokenException result = new JwtTokenException(JwtErrorType.MALFORMED_TOKEN, ex.getMessage(), ex);
        result.tokenValue = sanitizeToken(token);
        return result;
    }

    public static JwtTokenException fromUnsupportedJwtException(UnsupportedJwtException ex, String token) {
        JwtTokenException result = new JwtTokenException(JwtErrorType.UNSUPPORTED_TOKEN, ex.getMessage(), ex);
        result.tokenValue = sanitizeToken(token);
        return result;
    }

    public static JwtTokenException fromSecurityException(SecurityException ex, String token) {
        JwtTokenException result = new JwtTokenException(JwtErrorType.INVALID_SIGNATURE, ex.getMessage(), ex);
        result.tokenValue = sanitizeToken(token);
        return result;
    }

    public static JwtTokenException invalidToken(String token) {
        JwtTokenException result = new JwtTokenException(JwtErrorType.INVALID_TOKEN);
        result.tokenValue = sanitizeToken(token);
        return result;
    }

    public static JwtTokenException claimsParsingError(String message, Throwable cause) {
        return new JwtTokenException(JwtErrorType.CLAIMS_PARSING_ERROR, message, cause);
    }

    public static JwtTokenException tokenGenerationError(String message, Throwable cause) {
        return new JwtTokenException(JwtErrorType.TOKEN_GENERATION_ERROR, message, cause);
    }

    public static JwtTokenException invalidTokenType(String expected, String actual) {
        return new JwtTokenException(JwtErrorType.INVALID_TOKEN_TYPE, expected, actual, CorrelationId.generate());
    }

    private static String sanitizeToken(String token) {
        if (token == null || token.length() <= 8) {
            return token;
        }
        return token.substring(0, 8) + "...";
    }

    public boolean isRetryable() {
        return jwtErrorType == JwtErrorType.TOKEN_GENERATION_ERROR;
    }

    public boolean requiresReAuthentication() {
        return jwtErrorType == JwtErrorType.EXPIRED_TOKEN ||
                jwtErrorType == JwtErrorType.INVALID_SIGNATURE ||
                jwtErrorType == JwtErrorType.INVALID_TOKEN;
    }

}
