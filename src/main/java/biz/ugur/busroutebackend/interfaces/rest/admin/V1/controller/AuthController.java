package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.admin.application.usecase.admin.GetCurrentAdminUseCase;
import biz.ugur.busroutebackend.admin.application.usecase.auth.LoginUseCase;
import biz.ugur.busroutebackend.admin.application.usecase.auth.LogoutUseCase;
import biz.ugur.busroutebackend.admin.application.usecase.auth.RefreshTokenUseCase;
import biz.ugur.busroutebackend.admin.infrastructure.security.AdminPrincipal;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.admin.LoginRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.admin.RefreshTokenRequest;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.admin.AdminProfileResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.admin.AuthResponse;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_ADMIN_AUTH;

@RestController
@RequestMapping(V1_ADMIN_AUTH)
@CrossOrigin(origins = "*")
public class AuthController extends BaseController {

    private final LoginUseCase loginUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final GetCurrentAdminUseCase getCurrentAdminUseCase;

    public AuthController(LoginUseCase loginUseCase,
                         RefreshTokenUseCase refreshTokenUseCase,
                         LogoutUseCase logoutUseCase,
                         GetCurrentAdminUseCase getCurrentAdminUseCase,
                         MessageSource messageSource) {
        super(messageSource);
        this.loginUseCase = loginUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
        this.getCurrentAdminUseCase = getCurrentAdminUseCase;
    }

    @Override
    protected String getControllerName() {
        return AuthController.class.getSimpleName();
    }


    @PostMapping("/login")
    public Mono<ResponseEntity<ApiResponse<AuthResponse>>> login(@Valid @RequestBody LoginRequest request) {
        return ok(Mono.just(request.toRequest())
                .as(loginUseCase::execute)
                .map(AuthResponse::fromLogin));
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<ApiResponse<AuthResponse>>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return ok(Mono.just(new RefreshTokenUseCase.Request(request.refreshToken()))
                .as(refreshTokenUseCase::execute)
                .map(AuthResponse::fromRefresh));
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Void>> logout(@RequestHeader("Authorization") String authHeader) {
        return getCurrentPrincipal().flatMap(principal -> {
            String token = extractTokenFromHeader(authHeader);
            return logoutUseCase.execute(Mono.just(new LogoutUseCase.Request(principal.getId(), token)));
        }).then(noContent());
    }


    @GetMapping("/me")
    public Mono<ResponseEntity<ApiResponse<AdminProfileResponse>>> getCurrentAdmin() {
        return ok(getCurrentPrincipal()
                .flatMap(principal -> {
                    return getCurrentAdminUseCase.execute(Mono.just(new GetCurrentAdminUseCase.Query(principal.getId())))
                            .map(AdminProfileResponse::fromDomain);
                }));
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