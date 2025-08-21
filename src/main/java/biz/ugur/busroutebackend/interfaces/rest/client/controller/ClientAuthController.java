package biz.ugur.busroutebackend.interfaces.rest.client.controller;


import biz.ugur.busroutebackend.client.application.usecase.AuthenticateClientUseCase;
import biz.ugur.busroutebackend.client.application.usecase.CenterRegisterClientUseCase;
import biz.ugur.busroutebackend.client.application.usecase.RegisterClientUseCase;
import biz.ugur.busroutebackend.client.application.usecase.VerifyOtpUseCase;
import biz.ugur.busroutebackend.client.domain.enums.Platform;
import biz.ugur.busroutebackend.interfaces.rest.client.request.*;
import biz.ugur.busroutebackend.interfaces.rest.client.response.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/client/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class ClientAuthController {

    private final RegisterClientUseCase registerClientUseCase;
    private final VerifyOtpUseCase verifyOtpUseCase;
    private final AuthenticateClientUseCase authenticateClientUseCase;
    private final CenterRegisterClientUseCase centerRegisterClientUseCase;


    @PostMapping("/register")
    public Mono<ResponseEntity<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {

        RegisterClientUseCase.Command command = new RegisterClientUseCase.Command(
            request.name(),
            request.phone(),
            Platform.valueOf(request.platform().toUpperCase())
        );

        return Mono.just(command)
                .as(registerClientUseCase::execute)
                .map(result -> ResponseEntity.ok(new RegisterResponse(
                    result.clientId(),
                    result.name(),
                    maskPhone(result.phone()),
                    "OTP sent successfully",
                    result.status()
                )));
    }

    @PostMapping("/verify-otp")
    public Mono<ResponseEntity<VerifyOtpResponse>> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {

        VerifyOtpUseCase.Command command = new VerifyOtpUseCase.Command(
            request.phone(),
            request.otp()
        );

        return Mono.just(command)
                .as(verifyOtpUseCase::execute)
                .map(result -> ResponseEntity.ok(new VerifyOtpResponse(
                result.clientId(),
                result.verified(),
                "OTP verified successfully",
                result.status()
            )));

    }


    @PostMapping("/login")
    public Mono<ResponseEntity<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {

        AuthenticateClientUseCase.Command command = new AuthenticateClientUseCase.Command(request.phone(),request.otp());

        return Mono.just(command)
                .as(authenticateClientUseCase::execute)
                .map(result -> ResponseEntity.ok(new LoginResponse(
                result.clientId(),
                result.accessToken(),
                result.refreshToken(),
                "Login successful",
                result.status()
            )));

    }

    @PostMapping("/center-auth")
    public Mono<ResponseEntity<LoginResponse>> centerLogin(@Valid @RequestBody CenterRequest request) {

       return Mono.just(new CenterRegisterClientUseCase.Command(request.phone(), request.platform()))
               .as(centerRegisterClientUseCase::execute)
               .map(result -> ResponseEntity.ok(new LoginResponse(
                       result.clientId(),
                       result.accessToken(),
                       result.refreshToken(),
                       "Login successful",
                       result.status()
               )));

    }

    @PostMapping("/refresh")
    public Mono<ResponseEntity<RefreshTokenResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {

        // TODO: Создать RefreshTokenUseCase
          return Mono.just(ResponseEntity.ok(new RefreshTokenResponse(
            "new-access-token",
            "new-refresh-token",
            "Token refreshed successfully"
          )));

    }



    @PostMapping("/logout")
    public Mono<ResponseEntity<LogoutResponse>> logout(@RequestHeader("Authorization") String authHeader) {

        // TODO: Создать LogoutUseCase
        return Mono.just(ResponseEntity.ok(new LogoutResponse("Logout successful")));

    }



    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) {
            return "***";
        }
        return phone.substring(0, phone.length() - 4) + "****";
    }



}