package biz.ugur.busroutebackend.shared.infrastructure.exception;

import biz.ugur.busroutebackend.shared.domain.exception.AbstractDomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@Slf4j
@Component
@RequiredArgsConstructor
public class ErrorResponseFactory {


    public ErrorResponse fromDomainException(AbstractDomainException ex,
                                            ServerWebExchange exchange,
                                            HttpStatus status,
                                            Map<String, Object> metadata) {
        return ErrorResponse.builder()
                .status(status.value())
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .correlationId(ex.getCorrelationId().value())
                .timestamp(convertInstantToLocalDateTime(ex.getTimestamp()))
                .severity(ex.getSeverity().getDisplayName())
                .boundedContext(ex.getBoundedContext())
                .path(exchange.getRequest().getPath().value())
                .metadata(metadata)
                .build();
    }


    public ErrorResponse fromDomainException(AbstractDomainException ex,
                                            ServerWebExchange exchange,
                                            HttpStatus status) {
        return fromDomainException(ex, exchange, status, null);
    }


    public ErrorResponse fromDomainExceptionWithFieldErrors(AbstractDomainException ex,
                                                            ServerWebExchange exchange,
                                                            HttpStatus status,
                                                            Map<String, List<String>> fieldErrors,
                                                            Map<String, Object> metadata) {
        return ErrorResponse.builder()
                .status(status.value())
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .correlationId(ex.getCorrelationId().value())
                .timestamp(convertInstantToLocalDateTime(ex.getTimestamp()))
                .severity(ex.getSeverity().getDisplayName())
                .boundedContext(ex.getBoundedContext())
                .path(exchange.getRequest().getPath().value())
                .fieldErrors(fieldErrors)
                .metadata(metadata)
                .build();
    }


    public ErrorResponse fromGenericError(HttpStatus status,
                                         String errorCode,
                                         String message,
                                         ServerWebExchange exchange) {
        return ErrorResponse.of(status, errorCode, message, exchange.getRequest().getPath().value());
    }


    public ErrorResponse fromValidationError(HttpStatus status,
                                            String errorCode,
                                            String message,
                                            ServerWebExchange exchange,
                                            Map<String, List<String>> fieldErrors) {
        return ErrorResponse.withFieldErrors(
                status,
                errorCode,
                message,
                exchange.getRequest().getPath().value(),
                fieldErrors
        );
    }


    public ErrorResponse fromSimpleError(HttpStatus status,
                                        String errorCode,
                                        String message,
                                        String path) {
        return ErrorResponse.builder()
                .status(status.value())
                .errorCode(errorCode)
                .message(message)
                .correlationId(UUID.randomUUID().toString())
                .timestamp(LocalDateTime.now())
                .severity("ERROR")
                .boundedContext("system")
                .path(path)
                .build();
    }

    public Map<String, Object> createMetadata(String key, Object value) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(key, value);
        return metadata;
    }


    public Map<String, Object> createMetadata() {
        return new LinkedHashMap<>();
    }

    private static LocalDateTime convertInstantToLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, java.time.ZoneId.systemDefault());
    }
}
