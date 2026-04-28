package biz.ugur.busroutebackend.shared.infrastructure.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class PublicUploadsSecurityHeadersFilter implements WebFilter {

    private static final Set<String> PROTECTED_PATH_PREFIXES = Set.of(
            "/avatars/",
            "/banners/",
            "/ad-placements/",
            "/api/v1/avatars/",
            "/api/v1/banners/",
            "/api/v1/ad-placements/"
    );

    private static final String NOSNIFF = "nosniff";
    private static final String CSP_VALUE =
            "default-src 'none'; img-src 'self' data:; style-src 'unsafe-inline'; sandbox";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (matchesProtectedPath(path)) {
            exchange.getResponse().beforeCommit(() -> {
                HttpHeaders headers = exchange.getResponse().getHeaders();
                headers.set("X-Content-Type-Options", NOSNIFF);
                headers.set("Content-Security-Policy", CSP_VALUE);
                headers.set("X-Frame-Options", "DENY");
                headers.set("Referrer-Policy", "no-referrer");
                return Mono.empty();
            });
        }

        return chain.filter(exchange);
    }

    private boolean matchesProtectedPath(String path) {
        for (String prefix : PROTECTED_PATH_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
