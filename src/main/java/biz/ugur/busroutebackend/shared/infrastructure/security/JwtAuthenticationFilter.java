package biz.ugur.busroutebackend.shared.infrastructure.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements WebFilter {

    private static final String TOKEN_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        return extractToken(exchange)
                .flatMap(this::validateTokenNotBlacklisted)
                .flatMap(jwtService::extractAdminPrincipal)
                .map(this::createAuthentication)
                .flatMap(auth -> chain.filter(exchange)
                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth)))
                .onErrorResume(throwable -> {
                    log.debug("JWT authentication failed for path {}: {}", path, throwable.getMessage());
                    return chain.filter(exchange);
                });
    }


    private Mono<String> extractToken(ServerWebExchange exchange) {
        return Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .filter(header -> header.startsWith(TOKEN_PREFIX))
                .map(header -> header.substring(TOKEN_PREFIX.length()))
                .filter(token -> !token.trim().isEmpty())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("No valid token found")));
    }


    private Mono<String> validateTokenNotBlacklisted(String token) {
        return tokenBlacklistService.isAccessTokenBlacklisted(token)
                .flatMap(isBlacklisted -> {
                    if (isBlacklisted) {
                        log.debug("Access token found in blacklist, rejecting authentication");
                        return Mono.error(new IllegalArgumentException("Token is blacklisted"));
                    }
                    return Mono.just(token);
                })
                .onErrorResume(error -> {
                    // Если произошла ошибка при проверке blacklist, логируем и отклоняем токен
                    log.warn("Error checking token blacklist status, rejecting token: {}", error.getMessage());
                    return Mono.error(new IllegalArgumentException("Token validation failed"));
                });
    }


    private UsernamePasswordAuthenticationToken createAuthentication(AdminPrincipal principal) {
        var authorities = principal.roles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toSet());

        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));

        if (principal.isSuperAdmin()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        }

        var authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                authorities
        );

        authentication.setDetails(principal);
        return authentication;
    }


    private boolean isPublicPath(String path) {
        return path.startsWith("/actuator/health") ||
                path.startsWith("/actuator/info") ||
                path.startsWith("/api/public/") ||
                path.startsWith("/api/routes/") ||
                path.startsWith("/api/stops/") ||
                path.startsWith("/api/trips/") ||
                path.startsWith("/api/vehicles/") ||
                path.startsWith("/api/admin/auth/login") ||
                path.startsWith("/api/admin/auth/refresh") ||
                path.startsWith("/ws/");
    }
}