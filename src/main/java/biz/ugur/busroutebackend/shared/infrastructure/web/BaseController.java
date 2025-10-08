package biz.ugur.busroutebackend.shared.infrastructure.web;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;


@Slf4j
public abstract class BaseController {

    private final MessageSource messageSource;


    protected BaseController(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    protected abstract String getControllerName();

    protected MessageSource getMessageSource() {
        return messageSource;
    }

    protected String camelToSnake(String str) {
        return str.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
    }

    protected <T> Mono<ResponseEntity<ApiResponse<T>>> ok(Mono<T> data) {
        Objects.requireNonNull(data, "Data mono cannot be null");

        return data.map(d -> ResponseEntity.ok(ApiResponse.success(d)))
                .defaultIfEmpty(ResponseEntity.ok(ApiResponse.success(null)))
                .doOnSuccess(response -> logResponse("OK", response))
                .doOnError(error -> logError("OK", error));
    }


    protected <T> Mono<ResponseEntity<ApiResponse<T>>> created(Mono<T> data) {
        Objects.requireNonNull(data, "Data mono cannot be null");

        return data.map(d -> new ResponseEntity<>(ApiResponse.success(d), HttpStatus.CREATED))
                .doOnSuccess(response -> logResponse("CREATED", response))
                .doOnError(error -> logError("CREATED", error));
    }


    protected Mono<ResponseEntity<Void>> noContent() {
        log.debug("[{}] Returning NO_CONTENT", getControllerName());
        return Mono.just(ResponseEntity.noContent().build());
    }


    protected <T> Mono<ResponseEntity<ApiResponse<T>>> accepted(Mono<T> data) {
        Objects.requireNonNull(data, "Data mono cannot be null");

        return data.map(d -> new ResponseEntity<>(ApiResponse.success(d), HttpStatus.ACCEPTED))
                .doOnSuccess(response -> logResponse("ACCEPTED", response))
                .doOnError(error -> logError("ACCEPTED", error));
    }



    protected <T> Mono<ResponseEntity<ApiResponse<List<T>>>> okList(Flux<T> data) {
        Objects.requireNonNull(data, "Data flux cannot be null");

        return data.collectList()
                .map(list -> ResponseEntity.ok(ApiResponse.success(list)))
                .defaultIfEmpty(ResponseEntity.ok(ApiResponse.success(List.of())))
                .doOnSuccess(response -> logResponse("OK_LIST", response))
                .doOnError(error -> logError("OK_LIST", error));
    }


    protected <T> Mono<ResponseEntity<ApiResponse<PagedResponse<T>>>> okPaged(
            Flux<T> data, long total, int page, int size) {
        Objects.requireNonNull(data, "Data flux cannot be null");
        if (total < 0) throw new IllegalArgumentException("Total cannot be negative");
        if (page < 0) throw new IllegalArgumentException("Page cannot be negative");
        if (size <= 0) throw new IllegalArgumentException("Size must be positive");

        return data.collectList()
                .map(list -> {
                    PagedResponse<T> paged = new PagedResponse<>(list, total, page, size);
                    return ResponseEntity.ok(ApiResponse.success(paged));
                })
                .doOnSuccess(response -> logResponse("OK_PAGED", response))
                .doOnError(error -> logError("OK_PAGED", error));
    }


    protected <T> Mono<ResponseEntity<ApiResponse<List<T>>>> okListBuffered(Flux<T> data, int bufferSize) {
        Objects.requireNonNull(data, "Data flux cannot be null");
        if (bufferSize <= 0) throw new IllegalArgumentException("Buffer size must be positive");

        return data.buffer(bufferSize)
                .collectList()
                .map(batches -> {
                    List<T> allItems = new ArrayList<>();
                    for (List<T> batch : batches) {
                        allItems.addAll(batch);
                    }
                    return allItems;
                })
                .map(list -> ResponseEntity.ok(ApiResponse.success(list)))
                .defaultIfEmpty(ResponseEntity.ok(ApiResponse.success(List.of())))
                .doOnSuccess(response -> logResponse("OK_LIST_BUFFERED", response))
                .doOnError(error -> logError("OK_LIST_BUFFERED", error));
    }


    protected <T> Mono<ResponseEntity<ApiResponse<List<T>>>> okListBuffered(Flux<T> data) {
        return okListBuffered(data, 200);
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