package biz.ugur.busroutebackend.shared.infrastructure.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
public abstract class BaseJwtAuthenticationFilter<P extends UserDetails> implements WebFilter {

    private static final String TOKEN_PREFIX = "Bearer ";

    protected final BaseJwtTokenService<P> tokenService;
    protected final TokenBlacklistService tokenBlacklistService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isPublicPath(path)) {
            log.debug("Skipping JWT validation for public path: {}", path);
            return chain.filter(exchange);
        }

        return extractToken(exchange)
                .flatMap(this::validateTokenNotBlacklisted)
                .flatMap(tokenService::extractPrincipal)
                .map(this::createAuthentication)
                .flatMap(auth -> {
                    log.debug("JWT authentication successful for path: {} - User: {}",
                            path, auth.getName());
                    return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
                })
                .onErrorResume(throwable -> {
                    log.warn("JWT authentication failed for path {}: {}", path, throwable.getMessage());
                    return chain.filter(exchange);
                });
    }

    protected Mono<String> extractToken(ServerWebExchange exchange) {
        return Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .filter(header -> header.startsWith(TOKEN_PREFIX))
                .map(header -> header.substring(TOKEN_PREFIX.length()))
                .filter(token -> !token.trim().isEmpty())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("No valid token found")));
    }

    protected Mono<String> validateTokenNotBlacklisted(String token) {
        return tokenBlacklistService.isAccessTokenBlacklisted(token)
                .flatMap(isBlacklisted -> {
                    if (isBlacklisted) {
                        log.debug("Access token found in blacklist, rejecting authentication");
                        return Mono.error(new IllegalArgumentException("Token is blacklisted"));
                    }
                    return Mono.just(token);
                })
                .onErrorResume(error -> {
                    if ("Token is blacklisted".equals(error.getMessage())) {
                        return Mono.error(error);
                    }
                    log.warn("Error checking token blacklist: {}", error.getMessage());
                    return Mono.error(new IllegalArgumentException("Token validation failed"));
                });
    }

    protected abstract boolean isPublicPath(String path);

    protected UsernamePasswordAuthenticationToken createAuthentication(P principal) {
        return new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
    }
}
