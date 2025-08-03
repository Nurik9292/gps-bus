package biz.ugur.busroutebackend.shared.infrastructure.correlation;

import biz.ugur.busroutebackend.shared.domain.CorrelationId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
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
        CorrelationId correlationId = correlationIdGenerator.extractOrGenerate(exchange);

        correlationIdGenerator.addToResponse(exchange, correlationId);

        String method = exchange.getRequest().getMethod().name();
        String path = exchange.getRequest().getPath().value();
        String clientIp = getClientIp(exchange);

        log.info("Request started - {} {} - Client: {} - CorrelationId: {}",
                method, path, clientIp, correlationId.value());

        return chain.filter(exchange)
                .contextWrite(Context.of(CORRELATION_ID_CONTEXT_KEY, correlationId))
                .doOnSuccess(result ->
                        log.info("Request completed successfully - CorrelationId: {}", correlationId.value()))
                .doOnError(error ->
                        log.error("Request failed - CorrelationId: {} - Error: {}",
                                correlationId.value(), error.getMessage()))
                .doFinally(signalType ->
                        log.debug("Request finished - Signal: {} - CorrelationId: {}",
                                signalType, correlationId.value()));
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

        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }
}