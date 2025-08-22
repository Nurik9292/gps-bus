package biz.ugur.busroutebackend.shared.infrastructure.web;

import biz.ugur.busroutebackend.shared.domain.exception.AbstractDomainException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;


@Slf4j
public abstract class BaseController {

    private MessageSource messageSource;

    protected abstract String getControllerName();

    protected <T> Mono<ResponseEntity<ApiResponse<T>>> ok(Mono<T> data) {
        return data.map(d -> ResponseEntity.ok().body(ApiResponse.success(d)))
                .defaultIfEmpty(ResponseEntity.ok(ApiResponse.success(null)))
                .doOnSuccess(response -> logResponse("OK", response))
                .doOnError(error -> logError("OK", error));
    }


    protected <T> Mono<ResponseEntity<ApiResponse<T>>> created(Mono<T> data) {
        return data.map(d -> ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(d)))
                .doOnSuccess(response -> logResponse("CREATED", response))
                .doOnError(error -> logError("CREATED", error));
    }


    protected Mono<ResponseEntity<Void>> noContent() {
        log.debug("[{}] Returning NO_CONTENT", getControllerName());
        return Mono.just(ResponseEntity.noContent().build());
    }


    protected <T> Mono<ResponseEntity<ApiResponse<T>>> accepted(Mono<T> data) {
        return data.map(d -> ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(d)))
                .doOnSuccess(response -> logResponse("ACCEPTED", response))
                .doOnError(error -> logError("ACCEPTED", error));
    }



    protected <T> Mono<ResponseEntity<ApiResponse<List<T>>>> okList(Flux<T> data) {
        return data.collectList()
                .map(list -> ResponseEntity.ok().body(ApiResponse.success(list)))
                .defaultIfEmpty(ResponseEntity.ok(ApiResponse.success(List.of())))
                .doOnSuccess(response -> logResponse("OK_LIST", response))
                .doOnError(error -> logError("OK_LIST", error));
    }


    protected <T> Mono<ResponseEntity<ApiResponse<PagedResponse<T>>>> okPaged(
            Flux<T> data, long total, int page, int size) {
        return data.collectList()
                .map(list -> {
                    PagedResponse<T> paged = new PagedResponse<>(list, total, page, size);
                    return ResponseEntity.ok().body(ApiResponse.success(paged));
                })
                .doOnSuccess(response -> logResponse("OK_PAGED", response))
                .doOnError(error -> logError("OK_PAGED", error));
    }



    @ExceptionHandler(AbstractDomainException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleDomainException(AbstractDomainException ex) {
        log.error("[{}] Domain exception: {} - {}", getControllerName(), ex.getErrorCode(), ex.getMessage(), ex);

        String localizedMessage = getLocalizedMessage(ex.getErrorCode(), ex.getMessage());

        return Mono.just(ResponseEntity
                        .badRequest()
                        .body(ApiResponse.error(ex.getErrorCode(), localizedMessage))
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleResponseStatusException(ResponseStatusException ex) {
        log.error("[{}] Response status exception: {}", getControllerName(), ex.getMessage(), ex);

        String errorCode = "HTTP_" + ex.getStatusCode().value();
        String message = ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString();

        return Mono.just(ResponseEntity.status(ex.getStatusCode())
                .body(ApiResponse.error(errorCode, message)));
    }


    @ExceptionHandler({MethodArgumentNotValidException.class, WebExchangeBindException.class})
    public Mono<ResponseEntity<ApiResponse<Map<String, String>>>> handleValidationException(Exception ex) {
        log.error("[{}] Validation exception", getControllerName(), ex);
        Map<String, String> errors = new HashMap<>();

        if (ex instanceof MethodArgumentNotValidException validationEx) {
            errors = extractValidationErrors(validationEx.getBindingResult().getFieldErrors());
        } else if (ex instanceof WebExchangeBindException bindEx) {
            errors = extractValidationErrors(bindEx.getFieldErrors());
        }

        return Mono.just(ResponseEntity.badRequest()
                .body(ApiResponse.validationError(errors)));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleIllegalArgumentException(IllegalArgumentException ex) {
        log.error("[{}] Illegal argument: {}", getControllerName(), ex.getMessage(), ex);

        String localizedMessage = getLocalizedMessage(ErrorCode.INVALID_REQUEST.getCode(), ex.getMessage());

        return Mono.just(ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_REQUEST.getCode(), localizedMessage)));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleGenericException(Exception ex) {
        log.error("[{}] Unexpected error", getControllerName(), ex);

        String localizedMessage = getLocalizedMessage(
                ErrorCode.INTERNAL_ERROR.getCode(),
                ErrorCode.INTERNAL_ERROR.getDefaultMessage()
        );

        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.getCode(), localizedMessage)));
    }


    private Map<String, String> extractValidationErrors(List<FieldError> fieldErrors) {
        return fieldErrors.stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value",
                        (existing, replacement) -> existing
                ));
    }

    private String getLocalizedMessage(String code, String defaultMessage) {
        try {
            Locale locale = LocaleContextHolder.getLocale();
            return messageSource.getMessage(code, null, defaultMessage, locale);
        } catch (Exception e) {
            log.debug("No localized message found for code: {}", code);
            return defaultMessage;
        }
    }

    private void logResponse(String operation, ResponseEntity<?> response) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] {} response: status={}",
                    getControllerName(), operation, response.getStatusCode());
        }
    }

    private void logError(String operation, Throwable error) {
        log.error("[{}] Error during {} operation", getControllerName(), operation, error);
    }


    @Getter
    public enum ErrorCode {
        // Client errors (4xx)
        INVALID_REQUEST("INVALID_REQUEST", "Invalid request parameters"),
        VALIDATION_ERROR("VALIDATION_ERROR", "Validation failed"),
        NOT_FOUND("NOT_FOUND", "Resource not found"),
        UNAUTHORIZED("UNAUTHORIZED", "Authentication required"),
        FORBIDDEN("FORBIDDEN", "Access denied"),
        CONFLICT("CONFLICT", "Resource conflict"),

        INTERNAL_ERROR("INTERNAL_ERROR", "An unexpected error occurred"),
        SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", "Service temporarily unavailable"),
        EXTERNAL_SERVICE_ERROR("EXTERNAL_SERVICE_ERROR", "External service error");

        private final String code;
        private final String defaultMessage;

        ErrorCode(String code, String defaultMessage) {
            this.code = code;
            this.defaultMessage = defaultMessage;
        }

    }



    @Getter
    public static class ApiResponse<T> {
        private final boolean success;
        private final T data;
        private final String errorCode;
        private final String errorMessage;
        private final Instant timestamp;

        private ApiResponse(boolean success, T data, String errorCode, String errorMessage) {
            this.success = success;
            this.data = data;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.timestamp = Instant.now();
        }

        public static <T> ApiResponse<T> success(T data) {
            return new ApiResponse<>(true, data, null, null);
        }

        public static <T> ApiResponse<T> error(String errorCode, String errorMessage) {
            return new ApiResponse<>(false, null, errorCode, errorMessage);
        }

        public static ApiResponse<Map<String, String>> validationError(Map<String, String> errors) {
            return new ApiResponse<>(false, errors, ErrorCode.VALIDATION_ERROR.getCode(),
                    ErrorCode.VALIDATION_ERROR.getDefaultMessage());
        }

    }


    @Getter
    public static class PagedResponse<T> {
        private final List<T> content;
        private final long totalElements;
        private final int page;
        private final int size;
        private final int totalPages;

        public PagedResponse(List<T> content, long totalElements, int page, int size) {
            this.content = content;
            this.totalElements = totalElements;
            this.page = page;
            this.size = size;
            this.totalPages = (int) Math.ceil((double) totalElements / size);
        }

    }
}