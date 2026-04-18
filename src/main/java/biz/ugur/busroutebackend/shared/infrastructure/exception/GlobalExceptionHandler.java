package biz.ugur.busroutebackend.shared.infrastructure.exception;

import biz.ugur.busroutebackend.shared.application.compressor.DataCompressionException;
import biz.ugur.busroutebackend.shared.domain.exception.AbstractDomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.i18n.LocaleContextResolver;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Map;

@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final MessageSource messageSource;
    private final LocaleContextResolver localeContextResolver;
    private final ErrorResponseFactory errorResponseFactory;

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
}
