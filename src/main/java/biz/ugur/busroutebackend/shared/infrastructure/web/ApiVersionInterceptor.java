package biz.ugur.busroutebackend.shared.infrastructure.web;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Slf4j
@Component
public class ApiVersionInterceptor implements WebFilter {


    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        String version = extractVersion(path);

        if(version != null) {
            exchange.getResponse().getHeaders().add("X-API-Version", version);
        }

        HandlerMethod handler = getHandlerMethod(exchange);
        if (handler != null && handler.hasMethodAnnotation(DeprecatedApi.class)) {
            DeprecatedApi deprecated = handler.getMethodAnnotation(DeprecatedApi.class);

            exchange.getResponse().getHeaders().add("X-API-Deprecated", "true");
            exchange.getResponse().getHeaders().add("X-API-Deprecated-Since", Objects.requireNonNull(deprecated).since());
            exchange.getResponse().getHeaders().add("X-API-Remove-In", deprecated.removeIn());

            if (!deprecated.sunsetDate().isEmpty()) {
                exchange.getResponse().getHeaders().add("Sunset", deprecated.sunsetDate());
            }

            if (!deprecated.useInstead().isEmpty()) {
                exchange.getResponse().getHeaders().add("X-API-Successor", deprecated.useInstead());
            }

            log.warn("Deprecated API called: {} by {}", path,
                    exchange.getRequest().getRemoteAddress());
        }

        return chain.filter(exchange);
    }

    private String extractVersion(String path) {
        if (path.startsWith("/api/v")) {
            int endIndex = path.indexOf('/', 7);
            if (endIndex > 0) {
                return path.substring(5, endIndex);
            }
        }
        return null;
    }

    private HandlerMethod getHandlerMethod(ServerWebExchange exchange) {
        return null;
    }
}
