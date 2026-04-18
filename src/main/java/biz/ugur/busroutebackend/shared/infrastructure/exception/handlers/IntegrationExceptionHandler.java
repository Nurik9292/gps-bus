package biz.ugur.busroutebackend.shared.infrastructure.exception.handlers;

import biz.ugur.busroutebackend.integration.domain.exceptions.ExternalServiceBlockedException;
import biz.ugur.busroutebackend.integration.domain.exceptions.ExternalServiceNotFoundException;
import biz.ugur.busroutebackend.integration.domain.exceptions.RateLimitExceededException;
import biz.ugur.busroutebackend.integration.domain.exceptions.UnauthorizedEndpointException;
import biz.ugur.busroutebackend.shared.infrastructure.exception.ErrorResponse;
import biz.ugur.busroutebackend.shared.infrastructure.exception.ErrorResponseFactory;
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
public class IntegrationExceptionHandler {

    private final ErrorResponseFactory errorResponseFactory;

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
}
