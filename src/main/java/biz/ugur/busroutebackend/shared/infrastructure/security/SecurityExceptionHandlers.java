package biz.ugur.busroutebackend.shared.infrastructure.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;


@Slf4j
public final class SecurityExceptionHandlers {

    private SecurityExceptionHandlers() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }


    public static ServerAuthenticationEntryPoint authenticationEntryPoint() {
        return (exchange, ex) -> {
            String path = exchange.getRequest().getPath().value();
            log.warn("Authentication required for path: {} - {}", path, ex.getMessage());

            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().add("Content-Type", "application/json");

            String body = """
                {
                    "error": "unauthorized",
                    "message": "Authentication required. Please provide valid credentials.",
                    "timestamp": "%s",
                    "path": "%s"
                }
                """.formatted(
                    LocalDateTime.now(),
                    path
            );

            DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(reactor.core.publisher.Mono.just(buffer));
        };
    }


    public static ServerAccessDeniedHandler accessDeniedHandler() {
        return (exchange, denied) -> {
            String path = exchange.getRequest().getPath().value();
            log.warn("Access denied for path: {} - {}", path, denied.getMessage());

            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.FORBIDDEN);
            response.getHeaders().add("Content-Type", "application/json");

            String body = """
                {
                    "error": "access_denied",
                    "message": "Insufficient permissions. You do not have access to this resource.",
                    "timestamp": "%s",
                    "path": "%s"
                }
                """.formatted(
                    LocalDateTime.now(),
                    path
            );

            DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(reactor.core.publisher.Mono.just(buffer));
        };
    }
}
