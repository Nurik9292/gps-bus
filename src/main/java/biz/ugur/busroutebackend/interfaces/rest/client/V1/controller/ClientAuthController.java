package biz.ugur.busroutebackend.interfaces.rest.client.V1.controller;

import biz.ugur.busroutebackend.client.application.usecase.LogoutUseCase;
import biz.ugur.busroutebackend.client.application.usecase.RefreshTokenUseCase;
import biz.ugur.busroutebackend.client.infrastructure.security.ClientJwtTokenService;
import biz.ugur.busroutebackend.interfaces.rest.client.V1.request.*;
import biz.ugur.busroutebackend.interfaces.rest.client.V1.response.*;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_CLIENT_AUTH;

@RestController
@RequestMapping(V1_CLIENT_AUTH)
public class ClientAuthController extends BaseController {

    private final RefreshTokenUseCase refreshTokenUseCase;
    private final LogoutUseCase logoutUseCase;
    private final ClientJwtTokenService jwtTokenService;

    public ClientAuthController(RefreshTokenUseCase refreshTokenUseCase,
                               LogoutUseCase logoutUseCase,
                               ClientJwtTokenService jwtTokenService,
                               MessageSource messageSource) {
        super(messageSource);
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.logoutUseCase = logoutUseCase;
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected String getControllerName() {
        return ClientAuthController.class.getSimpleName();
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<ApiResponse<RefreshTokenResponse>>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshTokenUseCase.Command command = new RefreshTokenUseCase.Command(request.refreshToken());

        return ok(Mono.just(command)
                .as(refreshTokenUseCase::execute)
                .map(result -> new RefreshTokenResponse(
                    result.accessToken(),
                    result.refreshToken(),
                    "Token refreshed successfully"
                )));
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<ApiResponse<LogoutResponse>>> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "").trim();

        return jwtTokenService.getClientIdFromToken(token)
                .flatMap(clientId -> {
                    LogoutUseCase.Command command = new LogoutUseCase.Command(clientId);
                    return ok(Mono.just(command)
                            .as(logoutUseCase::execute)
                            .map(result -> new LogoutResponse(result.message())));
                })
                .onErrorResume(error ->
                    ok(Mono.just(new LogoutResponse("Logout failed: " + error.getMessage())))
                );
    }
}
