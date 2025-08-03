package biz.ugur.busroutebackend.admin.application.usecase.auth;

import biz.ugur.busroutebackend.admin.domain.exceptions.AdminNotFoundException;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminTokenException;
import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.shared.domain.CorrelationId;
import biz.ugur.busroutebackend.shared.infrastructure.security.JwtProperties;
import biz.ugur.busroutebackend.shared.infrastructure.security.JwtService;
import biz.ugur.busroutebackend.shared.infrastructure.security.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class RefreshTokenUseCase implements UseCase<Mono<RefreshTokenUseCase.Request>, Mono<RefreshTokenUseCase.Response>> {

    private final AdminRepository adminRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final TokenBlacklistService tokenBlacklistService;
    private final CorrelationContextService correlationService;

    public record Request(String refreshToken) {}

    public record Response(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            Admin admin,
            CorrelationId correlationId
    ) {}

    @Override
    public Mono<Response> execute(Mono<Request> request) {
        return correlationService.executeWithCorrelation(
                request.flatMap(this::executeWithCorrelation), "admin");
    }

    private Mono<Response> executeWithCorrelation(Request request) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    log.info("Processing token refresh - CorrelationId: {}", correlationId.value());

                    return validateAndExtractAdmin(request.refreshToken(), correlationId)
                            .flatMap(admin -> checkTokenBlacklist(request.refreshToken(), correlationId)
                                    .thenReturn(admin))
                            .flatMap(admin -> generateNewTokens(admin, correlationId))
                            .flatMap(result -> blacklistOldToken(request.refreshToken(), correlationId)
                                    .thenReturn(result))
                            .doOnSuccess(result ->
                                    log.info("Token refresh successful - CorrelationId: {} - Admin: {}",
                                            correlationId.value(), result.admin().getUsername()))
                            .doOnError(error ->
                                    log.warn("Token refresh failed - CorrelationId: {} - Error: {}",
                                            correlationId.value(), error.getMessage()));
                });
    }

    private Mono<Admin> validateAndExtractAdmin(String refreshToken, CorrelationId correlationId) {
        return jwtService.validateAndExtractClaims(refreshToken)
                .onErrorMap(ex -> AdminTokenException.invalidToken(refreshToken, correlationId))
                .filter(claims -> "refresh".equals(claims.get("type")))
                .switchIfEmpty(Mono.error(AdminTokenException.invalidTokenType("refresh", correlationId)))
                .map(claims -> AdminId.of(claims.getSubject()))
                .flatMap(this::findAdminById)
                .filter(Admin::getIsActive)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Token refresh failed - admin disabled - CorrelationId: {}", correlationId.value());
                    return Mono.error(AdminTokenException.adminDisabled("unknown", correlationId));
                }));
    }

    private Mono<Admin> findAdminById(AdminId adminId) {
        return adminRepository.findById(adminId)
                .switchIfEmpty(Mono.error(
                        new AdminNotFoundException(adminId.getValue(), "id", CorrelationId.forAdmin())));
    }

    private Mono<Void> checkTokenBlacklist(String token, CorrelationId correlationId) {
        return tokenBlacklistService.isRefreshTokenBlacklisted(token)
                .flatMap(isBlacklisted -> {
                    if (isBlacklisted) {
                        log.warn("Blacklisted token used - CorrelationId: {}", correlationId.value());
                        return Mono.error(AdminTokenException.blacklistedToken(token, correlationId));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> blacklistOldToken(String token, CorrelationId correlationId) {
        return tokenBlacklistService.blacklistRefreshToken(token)
                .doOnSuccess(v ->
                        log.debug("Old refresh token blacklisted - CorrelationId: {}", correlationId.value()))
                .onErrorResume(ex -> {
                    log.warn("Failed to blacklist old token - CorrelationId: {} - Error: {}",
                            correlationId.value(), ex.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Response> generateNewTokens(Admin admin, CorrelationId correlationId) {
        Set<String> roles = admin.getIsSuperAdmin() ? Set.of("ADMIN", "SUPER_ADMIN") : Set.of("ADMIN");

        return Mono.zip(
                        jwtService.generateAccessToken(admin.getId(), admin.getUsername(), roles, admin.getIsSuperAdmin()),
                        jwtService.generateRefreshToken(admin.getId())
                )
                .map(tokens -> new Response(
                        tokens.getT1(),
                        tokens.getT2(),
                        "Bearer",
                        jwtProperties.accessTokenExpiration().toSeconds(),
                        admin,
                        correlationId
                ))
                .onErrorMap(ex -> {
                    log.error("Token generation failed - CorrelationId: {} - Admin: {} - Error: {}",
                            correlationId.value(), admin.getUsername(), ex.getMessage());
                    return new AdminTokenException(AdminTokenException.TokenErrorType.TOKEN_GENERATION_FAILED,
                            "Failed to generate new tokens", admin.getUsername(), correlationId);
                });
    }
}
