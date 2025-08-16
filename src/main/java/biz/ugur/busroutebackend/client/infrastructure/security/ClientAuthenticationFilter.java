package biz.ugur.busroutebackend.client.infrastructure.security;

import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClientAuthenticationFilter implements WebFilter {

    private final JwtTokenService jwtTokenService;
    private final ClientRepository clientRepository;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().toString();

        if (isPublicEndpoint(path) || !isClientEndpoint(path)) {
            return chain.filter(exchange);
        }

        if (isPublicEndpoint(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return chain.filter(exchange);
        }

        String token = authHeader.substring(7);

        return authenticateToken(token)
                .flatMap(authentication -> {
                    return chain.filter(exchange)
                            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
                })
                .onErrorResume(error -> {
                    log.warn("Authentication failed: {}", error.getMessage());
                    return chain.filter(exchange);
                });
    }

    private Mono<UsernamePasswordAuthenticationToken> authenticateToken(String token) {
        try {
            String clientId = jwtTokenService.getClientIdFromToken(token);

            if (jwtTokenService.isTokenExpired(token)) {
                return Mono.error(new IllegalArgumentException("Token expired"));
            }

            return clientRepository.findById(ClientId.of(clientId))
                    .map(client -> {
                        ClientPrincipal principal = new ClientPrincipal(
                                client.getId().getValue(),
                                client.getPhoneNumber(),
                                client.getStatus().name()
                        );

                        return new UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                principal.getAuthorities()
                        );
                    });

        } catch (Exception e) {
            return Mono.error(new IllegalArgumentException("Invalid token"));
        }
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/api/v1/client/auth/") ||
                path.startsWith("/api/v1/mobile/") ||
                path.startsWith("/admin/") ||
                path.startsWith("/public/") ||
                path.startsWith("/routes/") ||
                path.startsWith("/stops/") ||
                path.startsWith("/trips/") ||
                path.startsWith("/vehicles/") ||
                path.startsWith("/trip-planning/") ||
                path.startsWith("/ws/") ||
                path.startsWith("/actuator/");
    }

    private boolean isClientEndpoint(String path) {
        return path.startsWith("/api/v1/client/") &&
                !path.startsWith("/api/v1/client/auth/");
    }
}