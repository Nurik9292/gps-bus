package biz.ugur.busroutebackend.admin.infrastructure.exception;

import biz.ugur.busroutebackend.admin.application.exceptions.AdminConcurrencyException;
import biz.ugur.busroutebackend.admin.application.exceptions.AdminOperationException;
import biz.ugur.busroutebackend.admin.domain.exceptions.*;
import biz.ugur.busroutebackend.shared.infrastructure.exception.ErrorResponse;
import biz.ugur.busroutebackend.shared.infrastructure.exception.ErrorResponseFactory;
import biz.ugur.busroutebackend.shared.infrastructure.exception.HttpStatusMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class AdminExceptionHandler {

    private final ErrorResponseFactory errorResponseFactory;

    @ExceptionHandler(AdminTokenException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAdminTokenException(
            AdminTokenException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Admin token error - CorrelationId: {} - Type: {} - Token: {}",
                ex.getCorrelationId().value(), ex.getTokenErrorType(), ex.getTokenValue());

        HttpStatus status = HttpStatusMapper.mapFromException(ex);
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("tokenErrorType", ex.getTokenErrorType().name());

        if (ex.getTokenValue() != null) {
            metadata.put("tokenValue", ex.getTokenValue());
        }

        if (ex.getAdminUsername() != null) {
            metadata.put("adminUsername", ex.getAdminUsername());
        }

        if (ex.getTokenExpiry() != null) {
            metadata.put("tokenExpiry", ex.getTokenExpiry());
        }

        metadata.put("requiresReAuthentication", ex.requiresReAuthentication());
        metadata.put("isRetryable", ex.isRetryable());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(AdminAuthenticationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAdminAuthenticationException(
            AdminAuthenticationException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Admin authentication failed - CorrelationId: {} - Type: {} - Username: {} - IP: {}",
                ex.getCorrelationId().value(), ex.getAuthErrorType(),
                ex.getUsername(), ex.getClientIp());

        HttpStatus status = HttpStatusMapper.mapFromException(ex);
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("authErrorType", ex.getAuthErrorType().name());
        metadata.put("attemptTime", ex.getAttemptTime());

        if (ex.getClientIp() != null) {
            metadata.put("clientIp", ex.getClientIp());
        }

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(AdminNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAdminNotFoundException(
            AdminNotFoundException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Admin not found - CorrelationId: {} - Identifier: {}",
                ex.getCorrelationId().value(), ex.getIdentifier());

        HttpStatus status = HttpStatus.NOT_FOUND;
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("identifierType", ex.getIdentifierType());
        metadata.put("identifier", ex.getIdentifier());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(AdminValidationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAdminValidationException(
            AdminValidationException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Admin validation error - CorrelationId: {} - Field: {} - Message: {}",
                ex.getCorrelationId().value(), ex.getFailedField(), ex.getMessage());

        HttpStatus status = HttpStatus.BAD_REQUEST;
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("failedField", ex.getFailedField());
        metadata.put("fieldErrors", ex.getFieldErrors());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(AdminOperationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAdminOperationException(
            AdminOperationException ex,
            ServerWebExchange exchange
    ) {
        log.error("Admin operation failed - CorrelationId: {} - Operation: {}",
                ex.getCorrelationId().value(), ex.getOperationType());

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("operationType", ex.getOperationType());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(AdminConcurrencyException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAdminConcurrencyException(
            AdminConcurrencyException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Admin concurrency conflict - CorrelationId: {}",
                ex.getCorrelationId().value());

        HttpStatus status = HttpStatus.CONFLICT;
        Map<String, Object> metadata = errorResponseFactory.createMetadata();

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }
}
