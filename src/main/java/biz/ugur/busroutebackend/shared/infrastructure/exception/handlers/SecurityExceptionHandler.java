package biz.ugur.busroutebackend.shared.infrastructure.exception.handlers;

import biz.ugur.busroutebackend.shared.infrastructure.exception.ErrorResponse;
import biz.ugur.busroutebackend.shared.infrastructure.exception.ErrorResponseFactory;
import biz.ugur.busroutebackend.shared.infrastructure.exception.HttpStatusMapper;
import biz.ugur.busroutebackend.shared.infrastructure.security.JwtTokenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class SecurityExceptionHandler {

    private final ErrorResponseFactory errorResponseFactory;

    @ExceptionHandler(JwtTokenException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleJwtTokenException(
            JwtTokenException ex,
            ServerWebExchange exchange
    ) {
        log.warn("JWT error - CorrelationId: {} - Type: {} - Token: {}",
                ex.getCorrelationId().value(), ex.getJwtErrorType(), ex.getTokenValue());

        HttpStatus status = HttpStatusMapper.mapFromException(ex);
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("jwtErrorType", ex.getJwtErrorType().name());

        if (ex.getTokenValue() != null) {
            metadata.put("tokenValue", ex.getTokenValue());
        }

        if (ex.getTokenExpiry() != null) {
            metadata.put("tokenExpiry", ex.getTokenExpiry());
        }

        if (ex.getExpectedTokenType() != null) {
            metadata.put("expectedTokenType", ex.getExpectedTokenType());
            metadata.put("actualTokenType", ex.getActualTokenType());
        }

        metadata.put("requiresReAuthentication", ex.requiresReAuthentication());
        metadata.put("isRetryable", ex.isRetryable());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAccessDeniedException(
            AccessDeniedException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Access denied: {}", ex.getMessage());

        ErrorResponse errorResponse = errorResponseFactory.fromGenericError(
                HttpStatus.FORBIDDEN,
                "ACCESS_DENIED",
                "Insufficient permissions",
                exchange
        );

        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(errorResponse));
    }

    @ExceptionHandler(AuthenticationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAuthenticationException(
            AuthenticationException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Authentication error: {}", ex.getMessage());

        ErrorResponse errorResponse = errorResponseFactory.fromGenericError(
                HttpStatus.UNAUTHORIZED,
                "AUTHENTICATION_REQUIRED",
                "Authentication required",
                exchange
        );

        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse));
    }
}
