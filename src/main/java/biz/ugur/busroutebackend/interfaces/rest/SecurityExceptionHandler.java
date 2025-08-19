package biz.ugur.busroutebackend.interfaces.rest;

import biz.ugur.busroutebackend.shared.domain.exception.AbstractDomainException;
import biz.ugur.busroutebackend.shared.infrastructure.security.JwtTokenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class SecurityExceptionHandler {


    @ExceptionHandler(JwtTokenException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleJwtException(
            JwtTokenException ex,
            ServerWebExchange exchange
    ) {
        log.warn("JWT error - CorrelationId: {} - Type: {} - Token: {}",
                ex.getCorrelationId().value(), ex.getJwtErrorType(), ex.getTokenValue());

        Map<String, Object> body = createErrorResponse(ex, exchange);
        body.put("jwtErrorType", ex.getJwtErrorType().name());

        if (ex.getTokenValue() != null) {
            body.put("tokenValue", ex.getTokenValue());
        }

        if (ex.getTokenExpiry() != null) {
            body.put("tokenExpiry", ex.getTokenExpiry());
        }

        if (ex.getExpectedTokenType() != null) {
            body.put("expectedTokenType", ex.getExpectedTokenType());
            body.put("actualTokenType", ex.getActualTokenType());
        }

        body.put("requiresReAuthentication", ex.requiresReAuthentication());
        body.put("isRetryable", ex.isRetryable());

        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAccessDeniedException(
            AccessDeniedException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Access denied: {}", ex.getMessage());

        Map<String, Object> body = Map.of(
                "error", "access_denied",
                "message", "Insufficient permissions",
                "timestamp", Instant.now(),
                "path", exchange.getRequest().getPath().value()
        );

        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(body));
    }

    @ExceptionHandler(AuthenticationException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleGenericAuthenticationException(
            AuthenticationException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Authentication error: {}", ex.getMessage());

        Map<String, Object> body = Map.of(
                "error", "authentication_required",
                "message", "Authentication required",
                "timestamp", Instant.now(),
                "path", exchange.getRequest().getPath().value()
        );

        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body));
    }

    private Map<String, Object> createErrorResponse(AbstractDomainException ex, ServerWebExchange exchange) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("errorCode", ex.getErrorCode());
        body.put("message", ex.getMessage());
        body.put("correlationId", ex.getCorrelationId().value());
        body.put("timestamp", ex.getTimestamp());
        body.put("severity", ex.getSeverity().getDisplayName());
        body.put("boundedContext", ex.getBoundedContext());
        body.put("path", exchange.getRequest().getPath().value());
        return body;
    }
}