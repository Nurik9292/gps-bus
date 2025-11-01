package biz.ugur.busroutebackend.admin.application.usecase.auth;

import biz.ugur.busroutebackend.admin.domain.exceptions.AdminAuthenticationException;
import biz.ugur.busroutebackend.admin.domain.model.Admin;
import biz.ugur.busroutebackend.admin.domain.repository.AdminRepository;
import biz.ugur.busroutebackend.admin.infrastructure.security.AdminPrincipal;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.admin.infrastructure.security.AdminJwtProperties;
import biz.ugur.busroutebackend.admin.infrastructure.security.AdminJwtTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class LoginUseCase extends BaseUseCase<Mono<LoginUseCase.Request>, LoginUseCase.Response> {

    private final AdminRepository adminRepository;
    private final AdminJwtTokenService adminJwtTokenService;
    private final AdminJwtProperties adminJwtProperties;

    public LoginUseCase(AdminRepository adminRepository,
                        AdminJwtTokenService adminJwtTokenService,
                        AdminJwtProperties adminJwtProperties,
                        CorrelationContextService correlationService,
                        EventBus eventBus) {
        super(correlationService, eventBus);
        this.adminRepository = adminRepository;
        this.adminJwtTokenService = adminJwtTokenService;
        this.adminJwtProperties = adminJwtProperties;
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
                                // Admin is immutable - updateLastLogin() returns a new instance
                                Admin updatedAdmin = admin.updateLastLogin();
                                return adminRepository.save(updatedAdmin);
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
        AdminPrincipal principal = AdminPrincipal.fromAdmin(admin);

        return Mono.zip(
                adminJwtTokenService.generateAccessToken(admin.getId(), admin.getUsername(), principal.getRoles(), admin.getIsSuperAdmin()),
                adminJwtTokenService.generateRefreshToken(admin.getId())
        ).map(tokens -> new Response(
                tokens.getT1(),
                tokens.getT2(),
                "Bearer",
                adminJwtProperties.getAccessTokenExpiration().toSeconds(),
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