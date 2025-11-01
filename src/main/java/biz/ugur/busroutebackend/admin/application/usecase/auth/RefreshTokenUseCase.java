package biz.ugur.busroutebackend.admin.application.usecase.auth;

import biz.ugur.busroutebackend.admin.domain.exceptions.AdminNotFoundException;
import biz.ugur.busroutebackend.admin.domain.exceptions.AdminTokenException;
import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.admin.infrastructure.security.AdminPrincipal;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.admin.infrastructure.security.AdminJwtProperties;
import biz.ugur.busroutebackend.admin.infrastructure.security.AdminJwtTokenService;
import biz.ugur.busroutebackend.shared.infrastructure.security.TokenBlacklistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class RefreshTokenUseCase extends BaseUseCase<Mono<RefreshTokenUseCase.Request>, RefreshTokenUseCase.Response> {

    private final AdminRepository adminRepository;
    private final AdminJwtTokenService adminJwtTokenService;
    private final AdminJwtProperties adminJwtProperties;
    private final TokenBlacklistService tokenBlacklistService;

    public RefreshTokenUseCase(AdminRepository adminRepository,
                               AdminJwtTokenService adminJwtTokenService,
                               AdminJwtProperties adminJwtProperties,
                               TokenBlacklistService tokenBlacklistService,
                               CorrelationContextService correlationService,
                               EventBus eventBus) {
        super(correlationService, eventBus);
        this.adminRepository = adminRepository;
        this.adminJwtTokenService = adminJwtTokenService;
        this.adminJwtProperties = adminJwtProperties;
        this.tokenBlacklistService = tokenBlacklistService;
    }


    @Override
    protected Mono<Response> process(Mono<Request> request) {
        return request.flatMap(this::processInternal);
    }


    private Mono<Response> processInternal(Request request) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> validateAndExtractAdmin(request.refreshToken(), correlationId)
                        .flatMap(admin -> checkTokenBlacklist(request.refreshToken(), correlationId)
                                .thenReturn(admin))
                        .flatMap(admin -> generateNewTokens(admin, correlationId))
                        .flatMap(response -> blacklistOldToken(request.refreshToken(), correlationId)
                                .thenReturn(response))
                        .doOnSuccess(result ->
                                log.info("Token refresh successful - CorrelationId={} - Admin={}",
                                        correlationId.value(), result.admin().getUsername()))
                        .doOnError(error ->
                                log.warn("Token refresh failed - CorrelationId={} - Error={}",
                                        correlationId.value(), error.getMessage()))
                );
    }


    @Override
    protected String getBoundContext() {
        return "admin";
    }


    private Mono<Admin> validateAndExtractAdmin(String refreshToken, CorrelationId correlationId) {
        return adminJwtTokenService.validateAndExtractClaims(refreshToken)
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
        AdminPrincipal principal = AdminPrincipal.fromAdmin(admin);

        return Mono.zip(
                        adminJwtTokenService.generateAccessToken(admin.getId(), admin.getUsername(), principal.getRoles(), admin.getIsSuperAdmin()),
                        adminJwtTokenService.generateRefreshToken(admin.getId())
                )
                .map(tokens -> new Response(
                        tokens.getT1(),
                        tokens.getT2(),
                        "Bearer",
                        adminJwtProperties.getAccessTokenExpiration().toSeconds(),
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


    public record Request(String refreshToken) {}

    public record Response(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            Admin admin,
            CorrelationId correlationId
    ) {}
}
