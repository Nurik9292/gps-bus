package biz.ugur.busroutebackend.shared.infrastructure.exception;

import biz.ugur.busroutebackend.admin.application.exceptions.AdminConcurrencyException;
import biz.ugur.busroutebackend.admin.application.exceptions.AdminOperationException;
import biz.ugur.busroutebackend.admin.domain.exceptions.*;
import biz.ugur.busroutebackend.admin.infrastructure.exception.AdminRepositoryException;
import biz.ugur.busroutebackend.banner.domain.exceptions.*;
import biz.ugur.busroutebackend.client.domain.exceptions.*;
import biz.ugur.busroutebackend.integration.domain.exceptions.*;
import biz.ugur.busroutebackend.notification.domain.exceptions.*;
import biz.ugur.busroutebackend.routing.domain.exceptions.*;
import biz.ugur.busroutebackend.shared.application.compressor.DataCompressionException;
import biz.ugur.busroutebackend.transport.domain.exceptions.*;
import biz.ugur.busroutebackend.shared.domain.exception.AbstractDomainException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import biz.ugur.busroutebackend.shared.infrastructure.security.JwtTokenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.i18n.LocaleContextResolver;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;
    private final LocaleContextResolver localeContextResolver;
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

    // ========================================
    // Admin Not Found Exception
    // ========================================

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

    // ========================================
    // Admin Delete Exception
    // ========================================

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

    // ========================================
    // Admin Already Exists Exception
    // ========================================

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

    // ========================================
    // Admin Password Exception
    // ========================================

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

    // ========================================
    // Admin Operation Exception
    // ========================================

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

    // ========================================
    // Admin Concurrency Exception
    // ========================================

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

    // ========================================
    // Admin Repository Exception
    // ========================================

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

    // ========================================
    // Admin Validation Exception
    // ========================================

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

    // ========================================
    // JWT Token Exception
    // ========================================

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

    // ========================================
    // Spring Security Exceptions
    // ========================================

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

    // ========================================
    // Validation Exceptions
    // ========================================

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleWebExchangeBindException(
            WebExchangeBindException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Request binding failed - Path: {} - Errors: {}",
                exchange.getRequest().getPath().value(), ex.getErrorCount());

        Map<String, List<String>> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.groupingBy(
                        FieldError::getField,
                        Collectors.mapping(
                                error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value",
                                Collectors.toList()
                        )
                ));

        ErrorResponse errorResponse = errorResponseFactory.fromValidationError(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "Request validation failed",
                exchange,
                fieldErrors
        );

        return Mono.just(ResponseEntity.badRequest().body(errorResponse));
    }

    // ========================================
    // Client Context Exceptions
    // ========================================

    @ExceptionHandler(ClientNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleClientNotFoundException(
            ClientNotFoundException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Client not found - CorrelationId: {} - Identifier: {} - Type: {}",
                ex.getCorrelationId().value(), ex.getIdentifier(), ex.getIdentifierType());

        HttpStatus status = HttpStatusMapper.mapFromException(ex);
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("identifier", ex.getIdentifier());
        metadata.put("identifierType", ex.getIdentifierType());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(ClientAlreadyExistsException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleClientAlreadyExistsException(
            ClientAlreadyExistsException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Client already exists - CorrelationId: {} - Identifier: {} - Type: {}",
                ex.getCorrelationId().value(), ex.getIdentifier(), ex.getIdentifierType());

        HttpStatus status = HttpStatusMapper.mapFromException(ex);
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("identifier", ex.getIdentifier());
        metadata.put("identifierType", ex.getIdentifierType());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(ClientValidationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleClientValidationException(
            ClientValidationException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Client validation error - CorrelationId: {} - Fields: {}",
                ex.getCorrelationId().value(), ex.getFieldErrors().keySet());

        HttpStatus status = HttpStatusMapper.mapFromException(ex);
        Map<String, List<String>> fieldErrors = ex.getFieldErrors();
        Map<String, Object> metadata = errorResponseFactory.createMetadata();

        if (ex.hasSingleFieldError()) {
            metadata.put("field", ex.getFailedField());
        }

        metadata.put("totalErrors", ex.getFieldErrors().size());
        metadata.put("affectedFields", ex.getFieldErrors().keySet());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainExceptionWithFieldErrors(
                ex, exchange, status, fieldErrors, metadata
        );

        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(ClientAuthenticationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleClientAuthenticationException(
            ClientAuthenticationException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Client authentication failed - CorrelationId: {} - Type: {} - Email: {}",
                ex.getCorrelationId().value(), ex.getAuthErrorType(), ex.getEmail());

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

    // ========================================
    // Transport Context Exceptions
    // ========================================

    @ExceptionHandler(RouteNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleRouteNotFoundException(
            RouteNotFoundException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Route not found - CorrelationId: {} - Identifier: {} - Type: {}",
                ex.getCorrelationId().value(), ex.getIdentifier(), ex.getIdentifierType());

        HttpStatus status = HttpStatusMapper.mapFromException(ex);
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("identifier", ex.getIdentifier());
        metadata.put("identifierType", ex.getIdentifierType());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(BusStopNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleBusStopNotFoundException(
            BusStopNotFoundException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Bus stop not found - CorrelationId: {} - Identifier: {} - Type: {}",
                ex.getCorrelationId().value(), ex.getIdentifier(), ex.getIdentifierType());

        HttpStatus status = HttpStatusMapper.mapFromException(ex);
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("identifier", ex.getIdentifier());
        metadata.put("identifierType", ex.getIdentifierType());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(VehicleNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleVehicleNotFoundException(
            VehicleNotFoundException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Vehicle not found - CorrelationId: {} - VehicleId: {}",
                ex.getCorrelationId().value(), ex.getVehicleId());

        HttpStatus status = HttpStatusMapper.mapFromException(ex);
        Map<String, Object> metadata = errorResponseFactory.createMetadata("vehicleId", ex.getVehicleId());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(VehicleAlreadyExistsException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleVehicleAlreadyExistsException(
            VehicleAlreadyExistsException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Vehicle already exists - CorrelationId: {} - Identifier: {}",
                ex.getCorrelationId().value(), ex.getIdentifier());

        HttpStatus status = HttpStatus.CONFLICT;
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        if (ex.getIdentifier() != null) {
            metadata.put("identifier", ex.getIdentifier());
        }

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(RouteAlreadyExistsException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleRouteAlreadyExistsException(
            RouteAlreadyExistsException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Route already exists - CorrelationId: {} - Identifier: {} - Type: {}",
                ex.getCorrelationId().value(), ex.getIdentifier(), ex.getIdentifierType());

        HttpStatus status = HttpStatus.CONFLICT;
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        if (ex.getIdentifier() != null) {
            metadata.put("identifier", ex.getIdentifier());
        }
        if (ex.getIdentifierType() != null) {
            metadata.put("identifierType", ex.getIdentifierType());
        }

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(RouteValidationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleRouteValidationException(
            RouteValidationException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Route validation error - CorrelationId: {} - Message: {}",
                ex.getCorrelationId().value(), ex.getMessage());

        HttpStatus status = HttpStatus.BAD_REQUEST;
        Map<String, Object> metadata = errorResponseFactory.createMetadata();

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(ShiftAssignmentNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleShiftAssignmentNotFoundException(
            ShiftAssignmentNotFoundException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Shift assignment not found - CorrelationId: {} - Identifier: {}",
                ex.getCorrelationId().value(), ex.getIdentifier());

        HttpStatus status = HttpStatus.NOT_FOUND;
        Map<String, Object> metadata = errorResponseFactory.createMetadata("identifier", ex.getIdentifier());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(ShiftAssignmentValidationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleShiftAssignmentValidationException(
            ShiftAssignmentValidationException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Shift assignment validation error - CorrelationId: {} - Message: {}",
                ex.getCorrelationId().value(), ex.getMessage());

        HttpStatus status = HttpStatus.BAD_REQUEST;
        Map<String, Object> metadata = errorResponseFactory.createMetadata();

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(RealTimeDataException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleRealTimeDataException(
            RealTimeDataException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Real-time data error - CorrelationId: {} - Type: {} - Vehicle: {} - Route: {}",
                ex.getCorrelationId().value(), ex.getDataErrorType(),
                ex.getVehicleId(), ex.getRouteCode());

        HttpStatus status = HttpStatus.SERVICE_UNAVAILABLE;
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("dataErrorType", ex.getDataErrorType().name());

        if (ex.getVehicleId() != null) {
            metadata.put("vehicleId", ex.getVehicleId());
        }

        if (ex.getRouteCode() != null) {
            metadata.put("routeCode", ex.getRouteCode());
        }

        metadata.put("isRetryable", ex.isRetryable());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    // ========================================
    // Routing Context Exceptions
    // ========================================

    @ExceptionHandler(RouteCalculationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleRouteCalculationException(
            RouteCalculationException ex,
            ServerWebExchange exchange
    ) {
        log.error("Route calculation error - CorrelationId: {} - Reason: {}",
                ex.getCorrelationId().value(), ex.getReason());

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        Map<String, Object> metadata = errorResponseFactory.createMetadata("reason", ex.getReason());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(InvalidLocationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleInvalidLocationException(
            InvalidLocationException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Invalid location - CorrelationId: {} - Type: {} - Value: {}",
                ex.getCorrelationId().value(), ex.getLocationType(), ex.getLocationValue());

        HttpStatus status = HttpStatusMapper.mapFromException(ex);
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("locationType", ex.getLocationType());
        metadata.put("locationValue", ex.getLocationValue());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    // ========================================
    // Banner Context Exceptions
    // ========================================

    @ExceptionHandler(BannerNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleBannerNotFoundException(
            BannerNotFoundException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Banner not found - CorrelationId: {} - BannerId: {}",
                ex.getCorrelationId().value(), ex.getBannerId());

        HttpStatus status = HttpStatus.NOT_FOUND;
        Map<String, Object> metadata = errorResponseFactory.createMetadata("bannerId", ex.getBannerId());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(BannerValidationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleBannerValidationException(
            BannerValidationException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Banner validation error - CorrelationId: {} - Fields: {}",
                ex.getCorrelationId().value(), ex.getFieldErrors().keySet());

        HttpStatus status = HttpStatus.BAD_REQUEST;
        Map<String, List<String>> fieldErrors = ex.getFieldErrors();
        Map<String, Object> metadata = errorResponseFactory.createMetadata();

        if (ex.hasSingleFieldError()) {
            metadata.put("field", ex.getFailedField());
        }

        metadata.put("totalErrors", ex.getFieldErrors().size());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainExceptionWithFieldErrors(
                ex, exchange, status, fieldErrors, metadata
        );

        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(BannerConflictException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleBannerConflictException(
            BannerConflictException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Banner conflict - CorrelationId: {} - BannerId: {} - Reason: {}",
                ex.getCorrelationId().value(), ex.getBannerId(), ex.getConflictReason());

        HttpStatus status = HttpStatus.CONFLICT;
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        if (ex.getBannerId() != null) {
            metadata.put("bannerId", ex.getBannerId());
        }
        metadata.put("conflictReason", ex.getConflictReason());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(BannerImageProcessingException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleBannerImageProcessingException(
            BannerImageProcessingException ex,
            ServerWebExchange exchange
    ) {
        log.error("Banner image processing error - CorrelationId: {} - Path: {} - Operation: {}",
                ex.getCorrelationId().value(), ex.getImagePath(), ex.getProcessingOperation());

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        if (ex.getImagePath() != null) {
            metadata.put("imagePath", ex.getImagePath());
        }
        if (ex.getProcessingOperation() != null) {
            metadata.put("operation", ex.getProcessingOperation());
        }

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(BannerEventSerializationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleBannerEventSerializationException(
            BannerEventSerializationException ex,
            ServerWebExchange exchange
    ) {
        log.error("Banner event serialization error - CorrelationId: {} - EventType: {} - Operation: {}",
                ex.getCorrelationId().value(), ex.getEventType(), ex.getOperation());

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        if (ex.getEventType() != null) {
            metadata.put("eventType", ex.getEventType());
        }
        if (ex.getOperation() != null) {
            metadata.put("operation", ex.getOperation());
        }

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    // ========================================
    // Notification Context Exceptions
    // ========================================

    @ExceptionHandler(NotificationNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleNotificationNotFoundException(
            NotificationNotFoundException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Notification not found - CorrelationId: {} - NotificationId: {}",
                ex.getCorrelationId().value(), ex.getNotificationId());

        HttpStatus status = HttpStatus.NOT_FOUND;
        Map<String, Object> metadata = errorResponseFactory.createMetadata("notificationId", ex.getNotificationId());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(NotificationValidationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleNotificationValidationException(
            NotificationValidationException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Notification validation error - CorrelationId: {} - Fields: {}",
                ex.getCorrelationId().value(), ex.getFieldErrors().keySet());

        HttpStatus status = HttpStatus.BAD_REQUEST;
        Map<String, List<String>> fieldErrors = ex.getFieldErrors();
        Map<String, Object> metadata = errorResponseFactory.createMetadata();

        if (ex.hasSingleFieldError()) {
            metadata.put("field", ex.getFailedField());
        }

        metadata.put("totalErrors", ex.getFieldErrors().size());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainExceptionWithFieldErrors(
                ex, exchange, status, fieldErrors, metadata
        );

        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    // ========================================
    // Integration Context Exceptions
    // ========================================

    @ExceptionHandler(ExternalServiceNotFoundException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleExternalServiceNotFoundException(
            ExternalServiceNotFoundException ex,
            ServerWebExchange exchange
    ) {
        log.warn("External service not found - CorrelationId: {} - Identifier: {} - Type: {}",
                ex.getCorrelationId().value(), ex.getServiceIdentifier(), ex.getIdentifierType());

        HttpStatus status = HttpStatus.NOT_FOUND;
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("serviceIdentifier", ex.getServiceIdentifier());
        metadata.put("identifierType", ex.getIdentifierType());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(ExternalServiceBlockedException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleExternalServiceBlockedException(
            ExternalServiceBlockedException ex,
            ServerWebExchange exchange
    ) {
        log.warn("External service blocked - CorrelationId: {} - Identifier: {} - Reason: {}",
                ex.getCorrelationId().value(), ex.getServiceIdentifier(), ex.getBlockReason());

        HttpStatus status = HttpStatus.FORBIDDEN;
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("serviceIdentifier", ex.getServiceIdentifier());
        if (ex.getBlockReason() != null) {
            metadata.put("blockReason", ex.getBlockReason());
        }

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(UnauthorizedEndpointException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleUnauthorizedEndpointException(
            UnauthorizedEndpointException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Unauthorized endpoint access - CorrelationId: {} - Service: {} - Endpoint: {}",
                ex.getCorrelationId().value(), ex.getServiceName(), ex.getEndpoint());

        HttpStatus status = HttpStatus.FORBIDDEN;
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("serviceName", ex.getServiceName());
        metadata.put("endpoint", ex.getEndpoint());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleRateLimitExceededException(
            RateLimitExceededException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Rate limit exceeded - CorrelationId: {} - Service: {} - Limit: {} - Current: {}",
                ex.getCorrelationId().value(), ex.getServiceName(), ex.getLimit(), ex.getCurrent());

        HttpStatus status = HttpStatus.TOO_MANY_REQUESTS;
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("serviceName", ex.getServiceName());
        metadata.put("limit", ex.getLimit());
        metadata.put("current", ex.getCurrent());
        metadata.put("isRetryable", ex.isRetryable());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    // ========================================
    // Shared Context Exceptions
    // ========================================

    @ExceptionHandler(DataCompressionException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleDataCompressionException(
            DataCompressionException ex,
            ServerWebExchange exchange
    ) {
        log.error("Data compression error - CorrelationId: {} - Operation: {} - DataType: {}",
                ex.getCorrelationId().value(), ex.getOperation(), ex.getDataType());

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        if (ex.getOperation() != null) {
            metadata.put("operation", ex.getOperation());
        }
        if (ex.getDataType() != null) {
            metadata.put("dataType", ex.getDataType());
        }

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    // ========================================
    // Domain Exceptions (Generic Fallback)
    // ========================================

    @ExceptionHandler(AbstractDomainException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleDomainException(
            AbstractDomainException ex,
            ServerWebExchange exchange
    ) {
        HttpStatus status = HttpStatusMapper.mapFromException(ex);

        log.error("Domain exception - CorrelationId: {} - ErrorCode: {} - Message: {} - Status: {}",
                ex.getCorrelationId().value(), ex.getErrorCode(), ex.getMessage(), status.value());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status);

        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

    // ========================================
    // Database Constraint Violations
    // ========================================

    @ExceptionHandler(DuplicateKeyException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleDuplicateKeyException(
            DuplicateKeyException ex,
            ServerWebExchange exchange
    ) {
        String constraintName = extractConstraintName(ex.getMessage());
        String userMessage = mapConstraintToMessage(constraintName, ex.getMessage());

        log.warn("Duplicate key violation - Path: {} - Constraint: {} - Message: {}",
                exchange.getRequest().getPath().value(), constraintName, userMessage);

        Map<String, List<String>> fieldErrors = mapConstraintToFieldErrors(constraintName);

        ErrorResponse errorResponse = errorResponseFactory.fromValidationError(
                HttpStatus.CONFLICT,
                "DUPLICATE_KEY",
                userMessage,
                exchange,
                fieldErrors
        );

        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex,
            ServerWebExchange exchange
    ) {
        String constraintName = extractConstraintName(ex.getMessage());
        String userMessage = mapConstraintToMessage(constraintName, ex.getMessage());

        log.warn("Data integrity violation - Path: {} - Constraint: {} - Message: {}",
                exchange.getRequest().getPath().value(), constraintName, userMessage);

        ErrorResponse errorResponse = errorResponseFactory.fromGenericError(
                HttpStatus.CONFLICT,
                "DATA_INTEGRITY_VIOLATION",
                userMessage,
                exchange
        );

        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse));
    }

    private String extractConstraintName(String message) {
        if (message == null) return "unknown";

        // Pattern for PostgreSQL constraint names
        if (message.contains("violates unique constraint")) {
            int start = message.indexOf("\"");
            int end = message.indexOf("\"", start + 1);
            if (start != -1 && end != -1) {
                return message.substring(start + 1, end);
            }
        }

        if (message.contains("violates foreign key constraint")) {
            int start = message.indexOf("\"");
            int end = message.indexOf("\"", start + 1);
            if (start != -1 && end != -1) {
                return message.substring(start + 1, end);
            }
        }

        return "unknown";
    }

    private String mapConstraintToMessage(String constraintName, String originalMessage) {
        return switch (constraintName) {
            case "bus_routes_route_number_key" -> "Маршрут с таким номером уже существует";
            case "bus_routes_route_name_key" -> "Маршрут с таким названием уже существует";
            case "bus_stops_name_key", "bus_stops_stop_name_key" -> "Остановка с таким названием уже существует";
            case "admins_username_key" -> "Администратор с таким логином уже существует";
            case "clients_email_key" -> "Пользователь с таким email уже существует";
            case "clients_phone_key" -> "Пользователь с таким телефоном уже существует";
            case "vehicles_gov_number_key" -> "Транспорт с таким гос. номером уже существует";
            case "banners_title_key" -> "Баннер с таким заголовком уже существует";
            case "cities_name_key" -> "Город с таким названием уже существует";
            case "external_services_name_key" -> "Внешний сервис с таким названием уже существует";
            default -> {
                if (originalMessage != null && originalMessage.contains("duplicate key")) {
                    yield "Запись с такими данными уже существует";
                }
                yield "Нарушение целостности данных";
            }
        };
    }

    private Map<String, List<String>> mapConstraintToFieldErrors(String constraintName) {
        Map<String, List<String>> fieldErrors = new LinkedHashMap<>();

        switch (constraintName) {
            case "bus_routes_route_number_key" ->
                    fieldErrors.put("routeNumber", List.of("Маршрут с таким номером уже существует"));
            case "bus_routes_route_name_key" ->
                    fieldErrors.put("routeName", List.of("Маршрут с таким названием уже существует"));
            case "bus_stops_name_key", "bus_stops_stop_name_key" ->
                    fieldErrors.put("name", List.of("Остановка с таким названием уже существует"));
            case "admins_username_key" ->
                    fieldErrors.put("username", List.of("Администратор с таким логином уже существует"));
            case "clients_email_key" ->
                    fieldErrors.put("email", List.of("Пользователь с таким email уже существует"));
            case "clients_phone_key" ->
                    fieldErrors.put("phone", List.of("Пользователь с таким телефоном уже существует"));
            case "vehicles_gov_number_key" ->
                    fieldErrors.put("govNumber", List.of("Транспорт с таким гос. номером уже существует"));
            case "banners_title_key" ->
                    fieldErrors.put("title", List.of("Баннер с таким заголовком уже существует"));
            case "cities_name_key" ->
                    fieldErrors.put("name", List.of("Город с таким названием уже существует"));
            case "external_services_name_key" ->
                    fieldErrors.put("name", List.of("Внешний сервис с таким названием уже существует"));
        }

        return fieldErrors;
    }

    // ========================================
    // Generic Exception (Fallback)
    // ========================================

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleIllegalArgumentException(
            IllegalArgumentException ex,
            ServerWebExchange exchange
    ) {
        log.error("Illegal argument: {}", ex.getMessage(), ex);

        String localizedMessage = getLocalizedMessage("INVALID_ARGUMENT", ex.getMessage(), exchange);

        ErrorResponse errorResponse = errorResponseFactory.fromGenericError(
                HttpStatus.BAD_REQUEST,
                "INVALID_ARGUMENT",
                localizedMessage,
                exchange
        );

        return Mono.just(ResponseEntity.badRequest().body(errorResponse));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleGenericException(
            Exception ex,
            ServerWebExchange exchange
    ) {
        log.error("Unexpected error - Path: {}", exchange.getRequest().getPath().value(), ex);

        String localizedMessage = getLocalizedMessage(
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                exchange
        );

        ErrorResponse errorResponse = errorResponseFactory.fromGenericError(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                localizedMessage,
                exchange
        );

        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
    }

    // ========================================
    // Helper Methods
    // ========================================

    private String getLocalizedMessage(String code, String defaultMessage, ServerWebExchange exchange) {
        try {
            Locale locale = localeContextResolver.resolveLocaleContext(exchange).getLocale();
            if (locale == null) {
                locale = Locale.forLanguageTag("ru");
            }
            return messageSource.getMessage(code, null, defaultMessage, locale);
        } catch (Exception e) {
            log.debug("No localized message found for code: {} - using default", code);
            return defaultMessage;
        }
    }

    private String getLocalizedMessage(String code, String defaultMessage) {
        try {
            Locale locale = Locale.forLanguageTag("ru");
            return messageSource.getMessage(code, null, defaultMessage, locale);
        } catch (Exception e) {
            log.debug("No localized message found for code: {} - using default", code);
            return defaultMessage;
        }
    }
}
