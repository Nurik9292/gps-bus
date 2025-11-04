package biz.ugur.busroutebackend.shared.infrastructure.web;

import biz.ugur.busroutebackend.shared.application.dto.PagedList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

@Slf4j
public abstract class BasePaginatedController extends BaseController {

    public static final int DEFAULT_PAGE = 1;

    public static final int DEFAULT_SIZE = 20;

    public static final int MIN_SIZE = 1;

    public static final int MAX_SIZE = 100;

    protected BasePaginatedController(MessageSource messageSource) {
        super(messageSource);
    }

    protected <T, P extends PagedList<T>> Mono<ResponseEntity<ApiResponse<P>>> okPaginated(
        Mono<P> pagedData
    ) {
        return pagedData
            .flatMap(data -> this.ok(Mono.just(data)))
            .doOnSuccess(this::logPaginatedResponse);
    }

    protected void validatePagination(int page, int size) {
        if (page < 1) {
            String message = String.format("Page must be >= 1, got: %d", page);
            log.warn("[{}] Pagination validation failed: {}", getControllerName(), message);
            throw new IllegalArgumentException(message);
        }
        if (size < MIN_SIZE || size > MAX_SIZE) {
            String message = String.format(
                "Size must be between %d and %d, got: %d",
                MIN_SIZE, MAX_SIZE, size
            );
            log.warn("[{}] Pagination validation failed: {}", getControllerName(), message);
            throw new IllegalArgumentException(message);
        }
    }

    private void logPaginatedResponse(ResponseEntity<?> response) {
        if (log.isDebugEnabled()) {
            log.debug("[{}] Paginated response: status={}",
                getControllerName(), response.getStatusCode());
        }
    }
}
