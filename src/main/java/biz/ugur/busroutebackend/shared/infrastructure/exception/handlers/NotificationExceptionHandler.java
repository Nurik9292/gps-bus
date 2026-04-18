package biz.ugur.busroutebackend.shared.infrastructure.exception.handlers;

import biz.ugur.busroutebackend.notification.domain.exceptions.NotificationNotFoundException;
import biz.ugur.busroutebackend.notification.domain.exceptions.NotificationValidationException;
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
public class NotificationExceptionHandler {

    private final ErrorResponseFactory errorResponseFactory;

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
}
