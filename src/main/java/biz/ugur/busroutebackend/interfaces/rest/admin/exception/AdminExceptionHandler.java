package biz.ugur.busroutebackend.interfaces.rest.admin.exception;


import biz.ugur.busroutebackend.admin.application.exceptions.AdminConcurrencyException;
import biz.ugur.busroutebackend.admin.application.exceptions.AdminOperationException;
import biz.ugur.busroutebackend.admin.domain.exceptions.*;
import biz.ugur.busroutebackend.admin.infrastructure.exception.AdminRepositoryException;
import biz.ugur.busroutebackend.shared.domain.AbstractDomainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class AdminExceptionHandler {

    @ExceptionHandler(AdminTokenException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleTokenException(
            AdminTokenException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Token error - CorrelationId: {} - Type: {} - Token: {}",
                ex.getCorrelationId().value(), ex.getTokenErrorType(), ex.getTokenValue());

        Map<String, Object> body = createErrorResponse(ex, exchange);
        body.put("tokenErrorType", ex.getTokenErrorType().name());

        if (ex.getTokenValue() != null) {
            body.put("tokenValue", ex.getTokenValue());
        }

        if (ex.getAdminUsername() != null) {
            body.put("adminUsername", ex.getAdminUsername());
        }

        if (ex.getTokenExpiry() != null) {
            body.put("tokenExpiry", ex.getTokenExpiry());
        }

        body.put("requiresReAuthentication", ex.requiresReAuthentication());
        body.put("isRetryable", ex.isRetryable());

        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body));
    }


    @ExceptionHandler(AdminAuthenticationException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAuthenticationException(
            AdminAuthenticationException ex, ServerWebExchange exchange) {

        log.warn("Authentication failed - CorrelationId: {} - Type: {} - Username: {} - IP: {}",
                ex.getCorrelationId().value(), ex.getAuthErrorType(),
                ex.getUsername(), ex.getClientIp());

        Map<String, Object> body = createErrorResponse(ex, exchange);
        body.put("authErrorType", ex.getAuthErrorType().name());
        body.put("attemptTime", ex.getAttemptTime());

        if (ex.getClientIp() != null) {
            body.put("clientIp", ex.getClientIp());
        }

        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body));
    }


    @ExceptionHandler(AdminNotFoundException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAdminNotFoundException(
            AdminNotFoundException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Admin not found - CorrelationId: {} - Message: {}", ex.getCorrelationId().value(), ex.getMessage());
        Map<String, Object> body = createErrorResponse(ex, exchange);

        body.put("identifier", ex.getIdentifier());
        body.put("identifierType", ex.getIdentifierType());

        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(body));
    }

    @ExceptionHandler(AdminDeleteException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAdminDeleteException(
            AdminDeleteException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Admin not delete - CorrelationId: {} - Message: {}", ex.getCorrelationId().value(), ex.getMessage());
        Map<String, Object> body = createErrorResponse(ex, exchange);

        body.put("adminId", ex.getAdminId());

        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(body));
    }


    @ExceptionHandler(AdminAlreadyExistsException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAdminAlreadyExists(
            AdminAlreadyExistsException ex,
            ServerWebExchange exchange) {

        log.warn("Admin already exists - CorrelationId: {} - Username: {} - Path: {}",
                ex.getCorrelationId().value(), ex.getUsername(), exchange.getRequest().getPath().value());

        Map<String, Object> body = createErrorResponse(ex, exchange);
        body.put("field", "username");
        body.put("username", ex.getUsername());

        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(body));
    }

    @ExceptionHandler(AdminOperationException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleOperationException(
            AdminOperationException ex, ServerWebExchange exchange) {

        log.error("Operation failed - CorrelationId: {} - Type: {} - Context: {}",
                ex.getCorrelationId().value(), ex.getOperationType(), ex.getOperationContext());

        Map<String, Object> body = createErrorResponse(ex, exchange);
        body.put("operationType", ex.getOperationType().name());

        if (ex.getOperationContext() != null) {
            body.put("operationContext", ex.getOperationContext());
        }

        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body));
    }

    @ExceptionHandler(AdminConcurrencyException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleConcurrencyException(
            AdminConcurrencyException ex, ServerWebExchange exchange) {

        log.warn("Concurrency conflict - CorrelationId: {} - EntityId: {} - Expected: {} - Actual: {}",
                ex.getCorrelationId().value(), ex.getEntityId(),
                ex.getExpectedVersion(), ex.getActualVersion());

        Map<String, Object> body = createErrorResponse(ex, exchange);
        body.put("entityId", ex.getEntityId());
        body.put("expectedVersion", ex.getExpectedVersion());
        body.put("actualVersion", ex.getActualVersion());

        if (ex.getConflictingOperation() != null) {
            body.put("conflictingOperation", ex.getConflictingOperation());
        }

        body.put("suggestion", "Please refresh the data and try again");

        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(body));
    }


    @ExceptionHandler(AdminRepositoryException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleRepositoryException(
            AdminRepositoryException ex, ServerWebExchange exchange) {

        log.error("Repository error - CorrelationId: {} - Type: {} - Operation: {}",
                ex.getCorrelationId().value(), ex.getErrorType(), ex.getOperation());

        Map<String, Object> body = createErrorResponse(ex, exchange);
        body.put("errorType", ex.getErrorType().name());
        body.put("operation", ex.getOperation());

        if (!"production".equals(System.getProperty("spring.profiles.active"))) {
            if (ex.getRootCause() != null) {
                body.put("technicalDetails", ex.getRootCause().getMessage());
            }
        }

        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body));
    }

    @ExceptionHandler(AdminValidationException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAdminValidationException(
            AdminValidationException ex,
            ServerWebExchange exchange) {

        log.warn("Admin validation error - CorrelationId: {} - Fields: {}",
                ex.getCorrelationId().value(), ex.getFieldErrors().keySet());

        Map<String, Object> body = createErrorResponse(ex, exchange);
        body.put("fieldErrors", ex.getFieldErrors());

        if (ex.hasSingleFieldError()) {
            body.put("field", ex.getFailedField());
            body.put("fieldError", ex.getFieldErrors().get(ex.getFailedField()).getFirst());
        }

        body.put("totalErrors", ex.getFieldErrors().size());
        body.put("affectedFields", ex.getFieldErrors().keySet());

        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body));
    }


    @ExceptionHandler(AdminDomainException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAdminDomainException(
            AdminDomainException ex,
            ServerWebExchange exchange) {

        log.error("Admin domain error - CorrelationId: {} - Error: {} - Message: {}",
                ex.getCorrelationId().value(), ex.getErrorCode(), ex.getMessage());

        Map<String, Object> body = createErrorResponse(ex, exchange);
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body));
    }


    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleBindException(
            WebExchangeBindException ex, ServerWebExchange exchange) {

        log.warn("Request binding failed - Path: {} - Errors: {}",
                exchange.getRequest().getPath().value(), ex.getErrorCount());

        Map<String, List<String>> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.groupingBy(
                        FieldError::getField,
                        Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList())
                ));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("errorCode", "ADMIN.BINDING_ERROR");
        body.put("message", "Request binding validation failed");
        body.put("fieldErrors", fieldErrors);
        body.put("timestamp", Instant.now());
        body.put("path", exchange.getRequest().getPath().value());

        return Mono.just(ResponseEntity.badRequest().body(body));
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

    private HttpStatus determineHttpStatus(AdminDomainException ex) {
        return switch (ex.getErrorCode()) {
            case "ADMIN.ALREADY_EXISTS" -> HttpStatus.CONFLICT;
            case "ADMIN.NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "ADMIN.VALIDATION_ERROR" -> HttpStatus.BAD_REQUEST;
            case "ADMIN.ACCESS_DENIED" -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }


}
