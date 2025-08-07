package biz.ugur.busroutebackend.interfaces.rest.admin.controller;

import biz.ugur.busroutebackend.admin.application.usecase.admin.GetCurrentAdminUseCase;
import biz.ugur.busroutebackend.admin.application.usecase.auth.LoginUseCase;
import biz.ugur.busroutebackend.admin.application.usecase.auth.LogoutUseCase;
import biz.ugur.busroutebackend.admin.application.usecase.auth.RefreshTokenUseCase;
import biz.ugur.busroutebackend.interfaces.rest.admin.request.admin.LoginRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.request.admin.RefreshTokenRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.response.admin.AdminProfileResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.response.admin.AuthResponse;
import biz.ugur.busroutebackend.shared.infrastructure.security.AdminPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final GetCurrentAdminUseCase getCurrentAdminUseCase;




    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        String username = request.username();
        log.info("Login attempt for username: {}", username);

        return Mono.just(request.toRequest())
                .as(loginUseCase::execute)
                .map(AuthResponse::fromLogin)
                .doOnSuccess(resp -> log.info("Login successful for username: {}", username))
                .doOnError(err -> log.warn("Login failed for username: {} - {}", username, err.getMessage(), err));
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        log.debug("Token refresh request received");

        return Mono.just(new RefreshTokenUseCase.Request(request.refreshToken()))
                .as(refreshTokenUseCase::execute)
                .map(AuthResponse::fromRefresh)
                .doOnSuccess(response -> log.debug("Token refresh successful"))
                .doOnError(error -> log.warn("Token refresh failed: {}", error.getMessage()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> logout(@RequestHeader("Authorization") String authHeader) {
        return getCurrentPrincipal().flatMap(principal -> {
            log.info("Logout request from admin: {}", principal.username());
            String token = extractTokenFromHeader(authHeader);
            return logoutUseCase.execute(Mono.just(new LogoutUseCase.Request(principal.id(), token)))
                    .doOnSuccess(v -> log.info("Logout successful for admin: {}", principal.username()))
                    .doOnError(error -> log.warn("Logout failed for admin {}: {}", principal.username(), error.getMessage()));
        });


    }


    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AdminProfileResponse> getCurrentAdmin() {
        return getCurrentPrincipal()
                .flatMap(principal -> {
                    log.debug("Get current admin info request: {}", principal.username());
                    return getCurrentAdminUseCase.execute(Mono.just(new GetCurrentAdminUseCase.Query(principal.id())))
                            .map(AdminProfileResponse::fromDomain);
                });
    }



    private String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Invalid authorization header");
    }

    private Mono<AdminPrincipal> getCurrentPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> (AdminPrincipal) auth.getPrincipal());
    }
}