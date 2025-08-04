package biz.ugur.busroutebackend.interfaces.rest.admin.controller;

import biz.ugur.busroutebackend.admin.application.usecase.*;
import biz.ugur.busroutebackend.admin.application.usecase.auth.LoginUseCase;
import biz.ugur.busroutebackend.admin.application.usecase.auth.LogoutUseCase;
import biz.ugur.busroutebackend.admin.application.usecase.auth.RefreshTokenUseCase;
import biz.ugur.busroutebackend.interfaces.rest.admin.request.AdminUpdateProfileRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.request.AvatarUpdateRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.request.LoginRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.request.RefreshTokenRequest;
import biz.ugur.busroutebackend.admin.application.dto.admin.AdminProfileResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.response.AuthResponse;
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
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AuthController {

    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final GetCurrentAdminUseCase getCurrentAdminUseCase;

    private final UpdateCurrentAdminProfileUseCase updateCurrentAdminProfileUseCase;
    private final UpdateCurrentAdminAvatarUseCase updateCurrentAdminAvatarUseCase;
    private final RemoveCurrentAdminAvatarUseCase removeCurrentAdminAvatarUseCase;

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        // ИСПРАВЛЕНО: убран Mono wrapper для @RequestBody
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
        // ИСПРАВЛЕНО: убран Mono wrapper для @RequestBody
        log.debug("Token refresh request received");

        return Mono.just(new RefreshTokenUseCase.Request(request.refreshToken()))
                .as(refreshTokenUseCase::execute)
                .map(AuthResponse::fromRefresh)
                .doOnSuccess(response -> log.debug("Token refresh successful"))
                .doOnError(error -> log.warn("Token refresh failed: {}", error.getMessage()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> logout(@AuthenticationPrincipal AdminPrincipal principal,
                             @RequestHeader("Authorization") String authHeader) {
        log.info("Logout request from admin: {}", principal.username());
        String token = extractTokenFromHeader(authHeader);

        return logoutUseCase.execute(Mono.just(new LogoutUseCase.Request(principal.id(), token)))
                .doOnSuccess(v -> log.info("Logout successful for admin: {}", principal.username()))
                .doOnError(error -> log.warn("Logout failed for admin {}: {}", principal.username(), error.getMessage()));
    }

    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AdminProfileResponse> getCurrentAdmin(@AuthenticationPrincipal AdminPrincipal principal) {
        log.debug("Get current admin info request: {}", principal.username());

        return getCurrentAdminUseCase.execute(Mono.just(new GetCurrentAdminUseCase.Query(principal.id())))
                .map(AdminProfileResponse::fromDomain)
                .doOnSuccess(response -> log.debug("Current admin info retrieved: {}", principal.username()))
                .doOnError(error -> log.warn("Failed to get current admin info for {}: {}", principal.username(), error.getMessage()));
    }

    @PatchMapping("/profile")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AdminProfileResponse> updateProfile(@AuthenticationPrincipal AdminPrincipal principal,
                                                    @Valid @RequestBody AdminUpdateProfileRequest request) {
        log.debug("Обновление профиля для админа: {}", principal.username());

        return updateCurrentAdminProfileUseCase.execute(Mono.just(
                        new UpdateCurrentAdminProfileUseCase.Request(
                                principal.id(),
                                request.getFullName(),
                                request.getAvatar()
                        )
                ))
                .map(AdminProfileResponse::fromDomain)
                .doOnSuccess(response -> log.info("✅ Профиль обновлен для: {}", principal.username()))
                .doOnError(error -> log.error("❌ Ошибка обновления профиля: {}", error.getMessage()));
    }

    @PatchMapping("/profile/avatar")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AdminProfileResponse> updateAvatar(@AuthenticationPrincipal AdminPrincipal principal,
                                                   @Valid @RequestBody AvatarUpdateRequest request) {
        log.debug("Обновление аватара для админа: {}", principal.username());

        return updateCurrentAdminAvatarUseCase.execute(
                        new UpdateCurrentAdminAvatarUseCase.Request(
                                principal.id(),
                                request.avatar()
                        ))
                .map(AdminProfileResponse::fromDomain)
                .doOnSuccess(response -> log.info("✅ Аватар обновлен для: {}", principal.username()))
                .doOnError(error -> log.error("❌ Ошибка обновления аватара: {}", error.getMessage()));
    }

    @DeleteMapping("/profile/avatar")
    @ResponseStatus(HttpStatus.OK)
    public Mono<AdminProfileResponse> removeAvatar(@AuthenticationPrincipal AdminPrincipal principal) {
        log.debug("Удаление аватара для админа: {}", principal.username());

        return removeCurrentAdminAvatarUseCase.execute(Mono.just(principal.id()))
                .map(AdminProfileResponse::fromDomain)
                .doOnSuccess(response -> log.info("✅ Аватар удален для: {}", principal.username()))
                .doOnError(error -> log.error("❌ Ошибка удаления аватара: {}", error.getMessage()));
    }

    private String extractTokenFromHeader(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Invalid authorization header");
    }
}