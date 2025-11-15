package biz.ugur.busroutebackend.integration.infrastructure.security;

import biz.ugur.busroutebackend.integration.domain.exceptions.ExternalServiceBlockedException;
import biz.ugur.busroutebackend.integration.domain.exceptions.RateLimitExceededException;
import biz.ugur.busroutebackend.integration.domain.exceptions.UnauthorizedEndpointException;
import biz.ugur.busroutebackend.integration.domain.model.ExternalService;
import biz.ugur.busroutebackend.integration.domain.repository.ExternalServiceRepository;
import biz.ugur.busroutebackend.integration.domain.valueobjects.ApiToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Set;


@Slf4j
@RequiredArgsConstructor
public class ApiTokenAuthenticationFilter implements WebFilter {

    private static final String TOKEN_PREFIX = "Bearer ";
    private static final String API_TOKEN_PREFIX = "brt_"; // Bus Route Token

    private final ExternalServiceRepository externalServiceRepository;
    private final ApiTokenRateLimiter rateLimiter;

    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "/api/v1/client/auth/",
            "/api/v1/admin/",
            "/actuator/",
            "/docs",
            "/api-docs"
    );

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (isExcludedPath(path) || !isMobileApiPath(path)) {
            return chain.filter(exchange);
        }

        return extractApiToken(exchange)
                .flatMap(this::authenticateExternalService)
                .flatMap(principal -> {
                    ExternalService service = getServiceFromPrincipal(principal);
                    service.validateEndpointAccess(path);

                    return checkRateLimit(principal)
                            .then(Mono.defer(() -> {
                                var auth = new UsernamePasswordAuthenticationToken(
                                        principal,
                                        null,
                                        principal.getAuthorities()
                                );
                                log.debug("External service authenticated: {} for path: {}",
                                        principal.getServiceName(), path);

                                return chain.filter(exchange)
                                        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
                            }));
                })
                .onErrorResume(error -> handleAuthenticationError(exchange, chain, error));
    }

    private Mono<String> extractApiToken(ServerWebExchange exchange) {
        return Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .filter(header -> header.startsWith(TOKEN_PREFIX))
                .map(header -> header.substring(TOKEN_PREFIX.length()))
                .filter(token -> token.startsWith(API_TOKEN_PREFIX))
                .switchIfEmpty(Mono.empty()); // No API token found, let it pass to client filter
    }

    private Mono<ApiTokenPrincipal> authenticateExternalService(String tokenValue) {
        ApiToken token = ApiToken.of(tokenValue);

        return externalServiceRepository.findByApiToken(token)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Invalid API token")))
                .flatMap(service -> {
                    if (!service.getIsActive()) {
                        return Mono.error(new ExternalServiceBlockedException(service.getName()));
                    }

                    ApiTokenPrincipal principal = new ApiTokenPrincipal(
                            service.getId().getValue(),
                            service.getName(),
                            service.getIsActive(),
                            service.getAllowedEndpoints(),
                            service.getRateLimitPerMinute()
                    );

                    return Mono.just(principal);
                });
    }

    private Mono<Void> checkRateLimit(ApiTokenPrincipal principal) {
        if (principal.getRateLimitPerMinute() == null) {
            return Mono.empty();
        }

        return rateLimiter.checkRateLimit(
                principal.getExternalServiceId(),
                principal.getRateLimitPerMinute()
        ).flatMap(allowed -> {
            if (!allowed) {
                return Mono.error(new RateLimitExceededException(
                        principal.getServiceName(),
                        principal.getRateLimitPerMinute(),
                        principal.getRateLimitPerMinute() + 1 // Approximate
                ));
            }
            return Mono.empty();
        });
    }

    private Mono<Void> handleAuthenticationError(ServerWebExchange exchange,
                                                   WebFilterChain chain,
                                                   Throwable error) {
        String path = exchange.getRequest().getPath().value();

        if (error instanceof ExternalServiceBlockedException ||
            error instanceof UnauthorizedEndpointException ||
            error instanceof RateLimitExceededException) {
            log.warn("External service authentication failed for path: {} - Error: {}",
                    path, error.getMessage());
            return Mono.error(error);
        }

        log.debug("API token authentication failed, continuing to next filter: {}", error.getMessage());
        return chain.filter(exchange);
    }

    private boolean isExcludedPath(String path) {
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }

    private boolean isMobileApiPath(String path) {
        return path.startsWith("/api/v1/mobile/");
    }

    private ExternalService getServiceFromPrincipal(ApiTokenPrincipal principal) {
        return ExternalService.builder()
                .name(principal.getServiceName())
                .allowedEndpoints(principal.getAllowedEndpoints())
                .build();
    }
}
