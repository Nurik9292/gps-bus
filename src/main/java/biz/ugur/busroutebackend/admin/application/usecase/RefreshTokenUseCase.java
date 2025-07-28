package biz.ugur.busroutebackend.admin.application.usecase;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.application.UseCase;
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
public class RefreshTokenUseCase implements UseCase<RefreshTokenUseCase.Request, Mono<RefreshTokenUseCase.Response>> {

    private final AdminRepository adminRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final TokenBlacklistService tokenBlacklistService;

    public record Request(String refreshToken) {}

    public record Response(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            Admin admin
    ) {}

    @Override
    public Mono<Response> execute(Request request) {
        log.debug("Processing token refresh request");

        return jwtService.validateAndExtractClaims(request.refreshToken())
                .filter(claims -> "refresh".equals(claims.get("type")))
                .switchIfEmpty(Mono.error(new TokenException("Invalid refresh token type")))
                .map(claims -> AdminId.of(claims.getSubject()))
                .flatMap(adminRepository::findById)
                .switchIfEmpty(Mono.error(new TokenException("Admin not found")))
                .filter(Admin::getIsActive)
                .switchIfEmpty(Mono.error(new TokenException("Account is disabled")))
                .flatMap(admin -> checkTokenBlacklist(request.refreshToken()).then(Mono.just(admin)))
                .flatMap(this::generateNewTokens)
                .flatMap(result -> blacklistOldToken(request.refreshToken()).then(Mono.just(result)))
                .doOnSuccess(result -> log.info("Token refresh successful for admin: {}", result.admin().getUsername()))
                .doOnError(error -> log.warn("Token refresh failed: {}", error.getMessage()));
    }

    private Mono<Void> checkTokenBlacklist(String token) {
        return tokenBlacklistService.isRefreshTokenBlacklisted(token)
                .flatMap(exists -> exists ?
                        Mono.error(new TokenException("Token is blacklisted")) :
                        Mono.empty());
    }

    private Mono<Void> blacklistOldToken(String token) {
        return tokenBlacklistService.blacklistRefreshToken(token);
    }

    private Mono<Response> generateNewTokens(Admin admin) {
        Set<String> roles = admin.getIsSuperAdmin() ? Set.of("ADMIN", "SUPER_ADMIN") : Set.of("ADMIN");

        return Mono.zip(
                jwtService.generateAccessToken(admin.getId(), admin.getUsername(), roles, admin.getIsSuperAdmin()),
                jwtService.generateRefreshToken(admin.getId())
        ).map(tokens -> new Response(
                tokens.getT1(),
                tokens.getT2(),
                "Bearer",
                jwtProperties.accessTokenExpiration().toSeconds(),
                admin
        ));
    }

    public static class TokenException extends RuntimeException {
        public TokenException(String message) {
            super(message);
        }
    }
}
