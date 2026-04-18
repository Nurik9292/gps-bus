package biz.ugur.busroutebackend.shared.infrastructure.exception.handlers;

import biz.ugur.busroutebackend.admin.application.exceptions.AdminConcurrencyException;
import biz.ugur.busroutebackend.admin.application.exceptions.AdminOperationException;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminAlreadyExistsException;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminAuthenticationException;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminDeleteException;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminNotFoundException;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminPasswordException;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminTokenException;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminValidationException;
import biz.ugur.busroutebackend.admin.infrastructure.exception.AdminRepositoryException;
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

import java.util.List;
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
        log.warn("Admin not found - CorrelationId: {} - Message: {}",
                ex.getCorrelationId().value(), ex.getMessage());

        HttpStatus status = HttpStatusMapper.mapFromException(ex);
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("identifier", ex.getIdentifier());
        metadata.put("identifierType", ex.getIdentifierType());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(AdminDeleteException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAdminDeleteException(
            AdminDeleteException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Admin delete failed - CorrelationId: {} - Message: {}",
                ex.getCorrelationId().value(), ex.getMessage());

        HttpStatus status = HttpStatus.BAD_REQUEST;
        Map<String, Object> metadata = errorResponseFactory.createMetadata("adminId", ex.getAdminId());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(AdminAlreadyExistsException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAdminAlreadyExistsException(
            AdminAlreadyExistsException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Admin already exists - CorrelationId: {} - Username: {} - Path: {}",
                ex.getCorrelationId().value(), ex.getUsername(),
                exchange.getRequest().getPath().value());

        HttpStatus status = HttpStatusMapper.mapFromException(ex);
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("field", "username");
        metadata.put("username", ex.getUsername());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(AdminPasswordException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAdminPasswordException(
            AdminPasswordException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Admin password error - CorrelationId: {} - Type: {} - Username: {}",
                ex.getCorrelationId().value(), ex.getErrorType(), ex.getUsername());

        HttpStatus status = ex.isAuthenticationFailure() ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST;
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("passwordErrorType", ex.getErrorType().name());

        if (!ex.getValidationErrors().isEmpty()) {
            ErrorResponse errorResponse = errorResponseFactory.fromDomainExceptionWithFieldErrors(
                    ex, exchange, status, ex.getValidationErrors(), metadata
            );
            return Mono.just(ResponseEntity.status(status).body(errorResponse));
        }

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(AdminOperationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAdminOperationException(
            AdminOperationException ex,
            ServerWebExchange exchange
    ) {
        log.error("Admin operation failed - CorrelationId: {} - Type: {} - Context: {}",
                ex.getCorrelationId().value(), ex.getOperationType(), ex.getOperationContext());

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("operationType", ex.getOperationType().name());

        if (ex.getOperationContext() != null) {
            metadata.put("operationContext", ex.getOperationContext());
        }

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(AdminConcurrencyException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAdminConcurrencyException(
            AdminConcurrencyException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Concurrency conflict - CorrelationId: {} - EntityId: {} - Expected: {} - Actual: {}",
                ex.getCorrelationId().value(), ex.getEntityId(),
                ex.getExpectedVersion(), ex.getActualVersion());

        HttpStatus status = HttpStatusMapper.mapFromException(ex);
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("entityId", ex.getEntityId());
        metadata.put("expectedVersion", ex.getExpectedVersion());
        metadata.put("actualVersion", ex.getActualVersion());

        if (ex.getConflictingOperation() != null) {
            metadata.put("conflictingOperation", ex.getConflictingOperation());
        }

        metadata.put("suggestion", "Please refresh the data and try again");

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(AdminRepositoryException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAdminRepositoryException(
            AdminRepositoryException ex,
            ServerWebExchange exchange
    ) {
        log.error("Repository error - CorrelationId: {} - Type: {} - Operation: {}",
                ex.getCorrelationId().value(), ex.getErrorType(), ex.getOperation());

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("errorType", ex.getErrorType().name());
        metadata.put("operation", ex.getOperation());

        if (!"production".equals(System.getProperty("spring.profiles.active"))) {
            if (ex.getRootCause() != null) {
                metadata.put("technicalDetails", ex.getRootCause().getMessage());
            }
        }

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(AdminValidationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAdminValidationException(
            AdminValidationException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Admin validation error - CorrelationId: {} - Fields: {}",
                ex.getCorrelationId().value(), ex.getFieldErrors().keySet());

        HttpStatus status = HttpStatusMapper.mapFromException(ex);
        Map<String, List<String>> fieldErrors = ex.getFieldErrors();
        Map<String, Object> metadata = errorResponseFactory.createMetadata();

        if (ex.hasSingleFieldError()) {
            metadata.put("field", ex.getFailedField());
            metadata.put("fieldError", ex.getFieldErrors().get(ex.getFailedField()).getFirst());
        }

        metadata.put("totalErrors", ex.getFieldErrors().size());
        metadata.put("affectedFields", ex.getFieldErrors().keySet());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainExceptionWithFieldErrors(
                ex, exchange, status, fieldErrors, metadata
        );

        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }
}
