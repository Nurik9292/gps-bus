package biz.ugur.busroutebackend.catalogsearch.infrastructure.web;

import biz.ugur.busroutebackend.catalogsearch.domain.exceptions.CatalogSearchDomainException;
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
public class CatalogSearchExceptionHandler {

    private final ErrorResponseFactory errorResponseFactory;

    @ExceptionHandler(CatalogSearchDomainException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleCatalogSearchException(
            CatalogSearchDomainException ex,
            ServerWebExchange exchange
    ) {
        HttpStatus status = HttpStatusMapper.mapFromException(ex);
        log.warn("Catalog-search rejected - CorrelationId: {} - Code: {} - Message: {}",
                ex.getCorrelationId().value(), ex.getBusinessCode(), ex.getMessage());

        Map<String, Object> metadata = errorResponseFactory.createMetadata();
        metadata.put("code", ex.getBusinessCode());
        ErrorResponse errorResponse = errorResponseFactory.fromDomainException(ex, exchange, status, metadata);
        return Mono.just(ResponseEntity.status(status).body(errorResponse));
    }
}
