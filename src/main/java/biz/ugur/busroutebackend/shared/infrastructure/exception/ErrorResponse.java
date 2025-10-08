package biz.ugur.busroutebackend.shared.infrastructure.exception;

import biz.ugur.busroutebackend.shared.domain.exception.AbstractDomainException;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;

import java.time.Instant;
import java.util.List;
import java.util.Map;


@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final int status;
    private final String errorCode;
    private final String message;
    private final String correlationId;
    private final Instant timestamp;
    private final String severity;
    private final String boundedContext;
    private final String path;
    private final Map<String, Object> metadata;
    private final Map<String, List<String>> fieldErrors;


    public static ErrorResponse from(AbstractDomainException ex, ServerWebExchange exchange, HttpStatus httpStatus) {
        return ErrorResponse.builder()
                .status(httpStatus.value())
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .correlationId(ex.getCorrelationId().value())
                .timestamp(ex.getTimestamp())
                .severity(ex.getSeverity().getDisplayName())
                .boundedContext(ex.getBoundedContext())
                .path(exchange.getRequest().getPath().value())
                .build();
    }

    public static ErrorResponse from(AbstractDomainException ex, ServerWebExchange exchange, HttpStatus httpStatus, Map<String, Object> metadata) {
        return ErrorResponse.builder()
                .status(httpStatus.value())
                .errorCode(ex.getErrorCode())
                .message(ex.getMessage())
                .correlationId(ex.getCorrelationId().value())
                .timestamp(ex.getTimestamp())
                .severity(ex.getSeverity().getDisplayName())
                .boundedContext(ex.getBoundedContext())
                .path(exchange.getRequest().getPath().value())
                .metadata(metadata)
                .build();
    }


    public static ErrorResponse of(HttpStatus httpStatus, String errorCode, String message, String path) {
        return ErrorResponse.builder()
                .status(httpStatus.value())
                .errorCode(errorCode)
                .message(message)
                .timestamp(Instant.now())
                .path(path)
                .build();
    }


    public static ErrorResponse withFieldErrors(HttpStatus httpStatus, String errorCode, String message, String path, Map<String, List<String>> fieldErrors) {
        return ErrorResponse.builder()
                .status(httpStatus.value())
                .errorCode(errorCode)
                .message(message)
                .timestamp(Instant.now())
                .path(path)
                .fieldErrors(fieldErrors)
                .build();
    }
}
