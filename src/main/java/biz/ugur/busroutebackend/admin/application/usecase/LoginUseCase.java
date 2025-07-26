package biz.ugur.busroutebackend.admin.application.usecase;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.shared.infrastructure.security.JwtProperties;
import biz.ugur.busroutebackend.shared.infrastructure.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class LoginUseCase implements UseCase<LoginUseCase.Request, Mono<LoginUseCase.Response>> {

    private final AdminRepository adminRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public record Request(String username, String password) {}

    public record Response(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            Admin admin
    ) {}

    @Override
    public Mono<Response> execute(Request request) {
        log.info("Processing login request for username: {}", request.username());

        return adminRepository.findByUsername(request.username())
                .switchIfEmpty(Mono.error(new AuthenticationException("Invalid username or password")))
                .filter(Admin::getIsActive)
                .switchIfEmpty(Mono.error(new AuthenticationException("Account is disabled")))
                .filter(admin -> admin.checkPassword(request.password()))
                .switchIfEmpty(Mono.error(new AuthenticationException("Invalid username or password")))
                .flatMap(admin -> {
                    // Update last login
                    admin.updateLastLogin();
                    return adminRepository.save(admin);
                })
                .flatMap(this::generateTokens)
                .doOnSuccess(result -> log.info("Successful login for admin: {}", result.admin().getUsername()))
                .doOnError(error -> log.warn("Login failed for username: {}: {}", request.username(), error.getMessage()));
    }

    private Mono<Response> generateTokens(Admin admin) {
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

    public static class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message) {
            super(message);
        }
    }
}