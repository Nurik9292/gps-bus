package biz.ugur.busroutebackend.admin.application.usecase.auth;

import biz.ugur.busroutebackend.admin.domain.exceptions.AdminAuthenticationException;
import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.shared.infrastructure.security.JwtProperties;
import biz.ugur.busroutebackend.shared.infrastructure.security.JwtService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Set;

@Slf4j
@Service
public class LoginUseCase extends BaseUseCase<Mono<LoginUseCase.Request>, LoginUseCase.Response> {

    private final AdminRepository adminRepository;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;

    public LoginUseCase(AdminRepository adminRepository,
                        JwtService jwtService,
                        JwtProperties jwtProperties,
                        CorrelationContextService correlationService,
                        EventBus eventBus) {
        super(correlationService, eventBus);
        this.adminRepository = adminRepository;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
    }

    @Override
    protected Mono<Response> process(Mono<Request> request) {
        return request.flatMap(this::processInternal);
    }

    private Mono<Response> processInternal(Request req) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    log.info("Authentication admin - CorrelationId: {} - Username: {}",
                            correlationId.value(), req.username());

                    return adminRepository.findByUsername(req.username())
                            .switchIfEmpty(Mono.error(new AdminAuthenticationException(
                                    AdminAuthenticationException.AuthErrorType.INVALID_CREDENTIALS, req.username(), correlationId)))
                            .filter(Admin::getIsActive)
                            .switchIfEmpty(Mono.error(new AdminAuthenticationException(
                                    AdminAuthenticationException.AuthErrorType.ACCOUNT_DISABLED, req.username(), correlationId)))
                            .filter(admin -> admin.checkPassword(req.password()))
                            .switchIfEmpty(Mono.error(new AdminAuthenticationException(
                                    AdminAuthenticationException.AuthErrorType.INVALID_CREDENTIALS, req.username(), correlationId)))
                            .flatMap(admin -> {
                                admin.updateLastLogin();
                                return adminRepository.save(admin);
                            })
                            .flatMap(this::generateTokens)
                            .doOnSuccess(result -> log.info("Successful login for admin: {}", result.admin().getUsername()))
                            .doOnError(error -> log.warn("Login failed for username: {}: {}", req.username(), error.getMessage()));
                });
    }


    @Override
    protected String getBoundContext() {
        return "admin";
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



    public record Request(String username, String password) {}

    public record Response(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            Admin admin
    ) {}

}