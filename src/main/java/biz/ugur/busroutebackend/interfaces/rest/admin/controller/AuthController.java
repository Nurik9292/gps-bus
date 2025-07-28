package biz.ugur.busroutebackend.interfaces.rest.admin.controller;

import biz.ugur.busroutebackend.admin.application.usecase.GetCurrentAdminUseCase;
import biz.ugur.busroutebackend.admin.application.usecase.LoginUseCase;
import biz.ugur.busroutebackend.admin.application.usecase.LogoutUseCase;
import biz.ugur.busroutebackend.admin.application.usecase.RefreshTokenUseCase;
import biz.ugur.busroutebackend.interfaces.rest.admin.dto.request.LoginRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.dto.request.RefreshTokenRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.dto.response.AdminProfileResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.dto.response.AuthResponse;
import biz.ugur.busroutebackend.shared.infrastructure.security.AdminPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final GetCurrentAdminUseCase getCurrentAdminUseCase;

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("Login attempt for username: {}", request.username());

        return loginUseCase.execute(new LoginUseCase.Request(
                        request.username(),
                        request.password()
                ))
                .map(result -> new AuthResponse(
                        result.accessToken(),
                        result.refreshToken(),
                        result.tokenType(),
                        result.expiresIn(),
                        new AdminProfileResponse(
                                result.admin().getId().getValue(),
                                result.admin().getUsername(),
                                result.admin().getFullName(),
                                result.admin().getIsSuperAdmin(),
                                result.admin().getIsActive(),
                                result.admin().getLastLoginAt()
                        )
                ))
                .doOnSuccess(response -> log.info("Login successful for username: {}", request.username()))
                .doOnError(error -> log.warn("Login failed for username: {}: {}", request.username(), error.getMessage()));
    }

    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        log.debug("Token refresh attempt");

        return refreshTokenUseCase.execute(new RefreshTokenUseCase.Request(
                        request.refreshToken()
                ))
                .map(result -> new AuthResponse(
                        result.accessToken(),
                        result.refreshToken(),
                        result.tokenType(),
                        result.expiresIn(),
                        new AdminProfileResponse(
                                result.admin().getId().getValue(),
                                result.admin().getUsername(),
                                result.admin().getFullName(),
                                result.admin().getIsSuperAdmin(),
                                result.admin().getIsActive(),
                                result.admin().getLastLoginAt()
                        )
                ))
                .doOnSuccess(response -> log.debug("Token refresh successful"))
                .doOnError(error -> log.warn("Token refresh failed: {}", error.getMessage()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> logout(
            @AuthenticationPrincipal AdminPrincipal principal,
            @RequestHeader("Authorization") String authHeader
    ) {
        log.info("Logout request from admin: {}", principal.username());

        String token = extractTokenFromHeader(authHeader);

        return logoutUseCase.execute(new LogoutUseCase.Request(
                        principal.id(),
                        token
                ))
                .doOnSuccess(v -> log.info("Logout successful for admin: {}", principal.username()))
                .doOnError(error -> log.warn("Logout failed for admin {}: {}", principal.username(), error.getMessage()));
    }

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AdminProfileResponse> getCurrentAdmin(@AuthenticationPrincipal AdminPrincipal principal) {
        log.debug("Get current admin info request: {}", principal.username());

        return getCurrentAdminUseCase.execute(new GetCurrentAdminUseCase.Query(
                        principal.id()
                ))
                .map(admin -> new AdminProfileResponse(
                        admin.getId().getValue(),
                        admin.getUsername(),
                        admin.getFullName(),
                        admin.getIsSuperAdmin(),
                        admin.getIsActive(),
                        admin.getLastLoginAt()
                ))
                .doOnSuccess(response -> log.debug("Current admin info retrieved: {}", principal.username()))
                .doOnError(error -> log.warn("Failed to get current admin info for {}: {}", principal.username(), error.getMessage()));
    }

    private String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Invalid authorization header");
    }
}