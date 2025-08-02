package biz.ugur.busroutebackend.interfaces.rest.admin.exception;

import biz.ugur.busroutebackend.admin.application.usecase.GetCurrentAdminUseCase;
import biz.ugur.busroutebackend.admin.application.usecase.LoginUseCase;
import biz.ugur.busroutebackend.admin.application.usecase.RefreshTokenUseCase;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminNotFoundException;
import biz.ugur.busroutebackend.shared.infrastructure.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class SecurityExceptionHandler {

    @ExceptionHandler(LoginUseCase.AuthenticationException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAuthenticationException(
            LoginUseCase.AuthenticationException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Authentication failed: {}", ex.getMessage());

        Map<String, Object> body = Map.of(
                "error", "authentication_failed",
                "message", ex.getMessage(),
                "timestamp", Instant.now(),
                "path", exchange.getRequest().getPath().value()
        );

        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body));
    }

    @ExceptionHandler(RefreshTokenUseCase.TokenException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleTokenException(
            RefreshTokenUseCase.TokenException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Token validation failed: {}", ex.getMessage());

        Map<String, Object> body = Map.of(
                "error", "invalid_token",
                "message", ex.getMessage(),
                "timestamp", Instant.now(),
                "path", exchange.getRequest().getPath().value()
        );

        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body));
    }

    @ExceptionHandler(JwtService.JwtTokenException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleJwtTokenException(
            JwtService.JwtTokenException ex,
            ServerWebExchange exchange
    ) {
        log.warn("JWT token error: {}", ex.getMessage());

        Map<String, Object> body = Map.of(
                "error", "jwt_error",
                "message", "Invalid or expired token",
                "timestamp", Instant.now(),
                "path", exchange.getRequest().getPath().value()
        );

        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body));
    }

    @ExceptionHandler(AdminNotFoundException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAdminNotFoundException(
            AdminNotFoundException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Admin not found: {}", ex.getMessage());

        Map<String, Object> body = Map.of(
                "error", "admin_not_found",
                "message", ex.getMessage(),
                "timestamp", Instant.now(),
                "path", exchange.getRequest().getPath().value()
        );

        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(body));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleAccessDeniedException(
            AccessDeniedException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Access denied: {}", ex.getMessage());

        Map<String, Object> body = Map.of(
                "error", "access_denied",
                "message", "Insufficient permissions",
                "timestamp", Instant.now(),
                "path", exchange.getRequest().getPath().value()
        );

        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(body));
    }

    @ExceptionHandler(AuthenticationException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleGenericAuthenticationException(
            AuthenticationException ex,
            ServerWebExchange exchange
    ) {
        log.warn("Authentication error: {}", ex.getMessage());

        Map<String, Object> body = Map.of(
                "error", "authentication_required",
                "message", "Authentication required",
                "timestamp", Instant.now(),
                "path", exchange.getRequest().getPath().value()
        );

        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body));
    }
}