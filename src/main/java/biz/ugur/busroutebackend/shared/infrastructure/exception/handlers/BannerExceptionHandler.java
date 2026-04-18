package biz.ugur.busroutebackend.shared.infrastructure.exception.handlers;

import biz.ugur.busroutebackend.banner.domain.exceptions.BannerConflictException;
import biz.ugur.busroutebackend.banner.domain.exceptions.BannerEventSerializationException;
import biz.ugur.busroutebackend.banner.domain.exceptions.BannerImageProcessingException;
import biz.ugur.busroutebackend.banner.domain.exceptions.BannerNotFoundException;
import biz.ugur.busroutebackend.banner.domain.exceptions.BannerValidationException;
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

import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class BannerExceptionHandler {

    private final ErrorResponseFactory errorResponseFactory;

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
}
