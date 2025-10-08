package biz.ugur.busroutebackend.shared.infrastructure.exception;

import biz.ugur.busroutebackend.admin.application.exceptions.AdminConcurrencyException;
import biz.ugur.busroutebackend.admin.application.exceptions.AdminOperationException;
import biz.ugur.busroutebackend.admin.domain.exceptions.*;
import biz.ugur.busroutebackend.admin.infrastructure.exception.AdminRepositoryException;
import biz.ugur.busroutebackend.client.domain.exceptions.*;
import biz.ugur.busroutebackend.routing.domain.exceptions.*;
import biz.ugur.busroutebackend.transport.domain.exceptions.*;
import biz.ugur.busroutebackend.shared.domain.exception.AbstractDomainException;
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

    // ========================================
    // Admin Token Exceptions
    // ========================================

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

    // ========================================
    // Admin Authentication Exceptions
    // ========================================

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
    // Domain Exceptions (Generic)
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

    @ExceptionHandler(TripPlanningException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleTripPlanningException(
            TripPlanningException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Trip planning error - CorrelationId: {} - Type: {} - Origin: {} - Destination: {}",
                ex.getCorrelationId().value(), ex.getPlanningErrorType(),
                ex.getOrigin(), ex.getDestination());

        HttpStatus status = HttpStatus.BAD_REQUEST;
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("planningErrorType", ex.getPlanningErrorType().name());

        if (ex.getOrigin() != null) {
            metadata.put("origin", ex.getOrigin());
        }

        if (ex.getDestination() != null) {
            metadata.put("destination", ex.getDestination());
        }

        metadata.put("isRetryable", ex.isRetryable());

        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }

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
    // Helper Methods
    // ========================================


    private String getLocalizedMessage(String code, String defaultMessage, ServerWebExchange exchange) {
        try {
            Locale locale = localeContextResolver.resolveLocaleContext(exchange).getLocale();
            if (locale == null) {
                locale = Locale.forLanguageTag("ru"); // Default fallback
            }
            return messageSource.getMessage(code, null, defaultMessage, locale);
        } catch (Exception e) {
            log.debug("No localized message found for code: {} - using default", code);
            return defaultMessage;
        }
    }


    private String getLocalizedMessage(String code, String defaultMessage) {
        try {
            Locale locale = Locale.forLanguageTag("ru"); // Default locale
            return messageSource.getMessage(code, null, defaultMessage, locale);
        } catch (Exception e) {
            log.debug("No localized message found for code: {} - using default", code);
            return defaultMessage;
        }
    }
}
