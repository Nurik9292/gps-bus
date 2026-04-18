package biz.ugur.busroutebackend.shared.infrastructure.exception.handlers;

import biz.ugur.busroutebackend.shared.infrastructure.exception.ErrorResponse;
import biz.ugur.busroutebackend.shared.infrastructure.exception.ErrorResponseFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.i18n.LocaleContextResolver;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ValidationExceptionHandler {

    private final MessageSource messageSource;
    private final LocaleContextResolver localeContextResolver;
    private final ErrorResponseFactory errorResponseFactory;

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
