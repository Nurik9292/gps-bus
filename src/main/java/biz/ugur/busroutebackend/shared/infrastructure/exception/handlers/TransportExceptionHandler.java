package biz.ugur.busroutebackend.shared.infrastructure.exception.handlers;

import biz.ugur.busroutebackend.shared.infrastructure.exception.ErrorResponse;
import biz.ugur.busroutebackend.shared.infrastructure.exception.ErrorResponseFactory;
import biz.ugur.busroutebackend.shared.infrastructure.exception.HttpStatusMapper;
import biz.ugur.busroutebackend.transport.domain.exceptions.AssignmentValidationException;
import biz.ugur.busroutebackend.transport.domain.exceptions.BusStopInUseException;
import biz.ugur.busroutebackend.transport.domain.exceptions.BusStopNotFoundException;
import biz.ugur.busroutebackend.transport.domain.exceptions.RealTimeDataException;
import biz.ugur.busroutebackend.transport.domain.exceptions.RouteAlreadyExistsException;
import biz.ugur.busroutebackend.transport.domain.exceptions.RouteNotFoundException;
import biz.ugur.busroutebackend.transport.domain.exceptions.RouteValidationException;
import biz.ugur.busroutebackend.transport.domain.exceptions.VehicleAlreadyExistsException;
import biz.ugur.busroutebackend.transport.domain.exceptions.VehicleNotFoundException;
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
public class TransportExceptionHandler {

    private final ErrorResponseFactory errorResponseFactory;

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

    @ExceptionHandler(BusStopInUseException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleBusStopInUseException(
            BusStopInUseException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Bus stop is used in active routes - Stop: {} - Routes: {}",
                ex.getStopId(), String.join(", ", ex.getRouteNumbers()));

        HttpStatus status = HttpStatusMapper.mapFromException(ex);
        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("stopId", ex.getStopId());
        metadata.put("routeNumbers", ex.getRouteNumbers());

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

    @ExceptionHandler(AssignmentValidationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAssignmentValidationException(
            AssignmentValidationException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Assignment validation error - Path: {} - Message: {}",
                exchange.getRequest().getPath().value(), ex.getMessage());

        ErrorResponse errorResponse = errorResponseFactory.fromGenericError(
                HttpStatus.CONFLICT,
                "ASSIGNMENT_CONFLICT",
                ex.getMessage(),
                exchange
        );

        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse));
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
}
