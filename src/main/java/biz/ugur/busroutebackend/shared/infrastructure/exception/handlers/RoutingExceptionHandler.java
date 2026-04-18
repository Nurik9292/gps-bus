package biz.ugur.busroutebackend.shared.infrastructure.exception.handlers;

import biz.ugur.busroutebackend.routing.domain.exceptions.InvalidLocationException;
import biz.ugur.busroutebackend.routing.domain.exceptions.RouteCalculationException;
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
public class RoutingExceptionHandler {

    private final ErrorResponseFactory errorResponseFactory;

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
}
