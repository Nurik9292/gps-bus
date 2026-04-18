package biz.ugur.busroutebackend.shared.infrastructure.exception.handlers;

import biz.ugur.busroutebackend.client.domain.exceptions.ClientAlreadyExistsException;
import biz.ugur.busroutebackend.client.domain.exceptions.ClientAuthenticationException;
import biz.ugur.busroutebackend.client.domain.exceptions.ClientNotFoundException;
import biz.ugur.busroutebackend.client.domain.exceptions.ClientValidationException;
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
public class ClientExceptionHandler {

    private final ErrorResponseFactory errorResponseFactory;

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
}
