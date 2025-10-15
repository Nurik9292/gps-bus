package biz.ugur.busroutebackend.interfaces.rest.client.V1.controller;

import biz.ugur.busroutebackend.client.application.usecase.AuthenticateClientUseCase;
import biz.ugur.busroutebackend.client.application.usecase.CenterRegisterClientUseCase;
import biz.ugur.busroutebackend.client.application.usecase.RegisterClientUseCase;
import biz.ugur.busroutebackend.client.application.usecase.VerifyOtpUseCase;
import biz.ugur.busroutebackend.client.domain.enums.Platform;
import biz.ugur.busroutebackend.interfaces.rest.client.V1.request.*;
import biz.ugur.busroutebackend.interfaces.rest.client.V1.response.*;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_CLIENT;

@RestController
@RequestMapping(V1_CLIENT + "/auth")
@CrossOrigin(origins = "*")
public class ClientAuthController extends BaseController {

    private final RegisterClientUseCase registerClientUseCase;
    private final VerifyOtpUseCase verifyOtpUseCase;
    private final AuthenticateClientUseCase authenticateClientUseCase;
    private final CenterRegisterClientUseCase centerRegisterClientUseCase;

    public ClientAuthController(RegisterClientUseCase registerClientUseCase,
                               VerifyOtpUseCase verifyOtpUseCase,
                               AuthenticateClientUseCase authenticateClientUseCase,
                               CenterRegisterClientUseCase centerRegisterClientUseCase,
                               MessageSource messageSource) {
        super(messageSource);
        this.registerClientUseCase = registerClientUseCase;
        this.verifyOtpUseCase = verifyOtpUseCase;
        this.authenticateClientUseCase = authenticateClientUseCase;
        this.centerRegisterClientUseCase = centerRegisterClientUseCase;
    }

    @Override
    protected String getControllerName() {
        return ClientAuthController.class.getSimpleName();
    }


    @PostMapping("/register")
    public Mono<ResponseEntity<ApiResponse<RegisterResponse>>> register(@Valid @RequestBody RegisterRequest request) {
        RegisterClientUseCase.Command command = new RegisterClientUseCase.Command(
            request.name(),
            request.phone(),
            Platform.valueOf(request.platform().toUpperCase())
        );

        return ok(Mono.just(command)
                .as(registerClientUseCase::execute)
                .map(result -> new RegisterResponse(
                    result.clientId(),
                    result.name(),
                    maskPhone(result.phone()),
                    "OTP sent successfully",
                    result.status()
                )));
    }

    @PostMapping("/verify-otp")
    public Mono<ResponseEntity<ApiResponse<VerifyOtpResponse>>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        VerifyOtpUseCase.Command command = new VerifyOtpUseCase.Command(
            request.phone(),
            request.otp()
        );

        return ok(Mono.just(command)
                .as(verifyOtpUseCase::execute)
                .map(result -> new VerifyOtpResponse(
                result.clientId(),
                result.verified(),
                "OTP verified successfully",
                result.status()
            )));
    }


    @PostMapping("/login")
    public Mono<ResponseEntity<ApiResponse<LoginResponse>>> login(@Valid @RequestBody LoginRequest request) {
        AuthenticateClientUseCase.Command command = new AuthenticateClientUseCase.Command(request.phone(), request.otp());

        return ok(Mono.just(command)
                .as(authenticateClientUseCase::execute)
                .map(result -> new LoginResponse(
                result.clientId(),
                result.accessToken(),
                result.refreshToken(),
                "Login successful",
                result.status()
            )));
    }

    @PostMapping("/center-auth")
    public Mono<ResponseEntity<ApiResponse<LoginResponse>>> centerLogin(@Valid @RequestBody CenterRequest request) {
        return ok(Mono.just(new CenterRegisterClientUseCase.Command(request.phone()))
               .as(centerRegisterClientUseCase::execute)
               .map(result -> new LoginResponse(
                       result.clientId(),
                       result.accessToken(),
                       result.refreshToken(),
                       "Login successful",
                       result.status()
               )));
    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<ApiResponse<RefreshTokenResponse>>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        // TODO: Создать RefreshTokenUseCase
        return ok(Mono.just(new RefreshTokenResponse(
            "new-access-token",
            "new-refresh-token",
            "Token refreshed successfully"
        )));
    }



    @PostMapping("/logout")
    public Mono<ResponseEntity<ApiResponse<LogoutResponse>>> logout(@RequestHeader("Authorization") String authHeader) {
        // TODO: Создать LogoutUseCase
        return ok(Mono.just(new LogoutResponse("Logout successful")));
    }



    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "***";
        }
        return phone.substring(0, phone.length() - 4) + "****";
    }



}