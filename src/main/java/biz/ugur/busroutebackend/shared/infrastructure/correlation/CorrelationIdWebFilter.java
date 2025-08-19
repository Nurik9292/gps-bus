package biz.ugur.busroutebackend.shared.infrastructure.correlation;

import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class CorrelationIdWebFilter implements WebFilter {

    private static final String CORRELATION_ID_CONTEXT_KEY = "correlationId";
    private static final String CORRELATION_ID_MDC_KEY = "correlationId";

    private final CorrelationIdGenerator correlationIdGenerator;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();
        String clientIp = getClientIp(exchange);

        if (method.equalsIgnoreCase("HEAD") || path.contains("/avatars/") || path.contains("/banners/")) {
            return chain.filter(exchange);
        }


        CorrelationId correlationId = correlationIdGenerator.extractOrGenerate(exchange);
        correlationIdGenerator.addToResponse(exchange, correlationId);

        if (shouldSkipDetailedLogging(path)) {
            return chain.filter(exchange)
                    .contextWrite(Context.of(CORRELATION_ID_CONTEXT_KEY, correlationId));
        }

        log.info("Request started - {} {} - Client: {} - CorrelationId: {}",
                method, path, clientIp, correlationId.value());

        return chain.filter(exchange)
                .contextWrite(Context.of(CORRELATION_ID_CONTEXT_KEY, correlationId))
                .doOnSuccess(result ->
                        log.info("Request completed successfully - CorrelationId: {}", correlationId.value()))
                .doOnError(error -> {
                    log.error("Request failed - {} {} - CorrelationId: {} - Error: {} - Client: {}",
                            method, path, correlationId.value(), error.getMessage(), clientIp);

                    if (log.isDebugEnabled() && !(error instanceof WebExchangeBindException)) {
                        log.debug("Request error details", error);
                    }
                })
                .doFinally(signalType ->
                        log.debug("Request finished - Signal: {} - CorrelationId: {}",
                                signalType, correlationId.value()));
    }

    private boolean shouldSkipDetailedLogging(String path) {
        return path.startsWith("/actuator") ||
                path.startsWith("/health") ||
                path.contains("/favicon.ico") ||
                path.contains("/static/") ||
                path.contains("/css/") ||
                path.contains("/js/") ||
                path.contains("/avatars/");
    }

    private String getClientIp(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        String cfConnectingIp = exchange.getRequest().getHeaders().getFirst("CF-Connecting-IP");
        if (cfConnectingIp != null && !cfConnectingIp.isEmpty()) {
            return cfConnectingIp;
        }

        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }
}