package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.client.infrastructure.security.JwtTokenService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AuthenticateClientUseCase implements UseCase<AuthenticateClientUseCase.Command, Mono<AuthenticateClientUseCase.Result>> {

    private final ClientRepository clientRepository;
    private final JwtTokenService jwtTokenService;

    @Override
    public Mono<Result> execute(Command command) {
        return clientRepository.findByPhone(command.phone())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Client not found")))
                .flatMap(client -> {
                    if (!client.isOtpVerified()) {
                        return Mono.error(new IllegalArgumentException("Phone not verified"));
                    }

                    if (!client.isActive()) {
                        return Mono.error(new IllegalArgumentException("Client account not active"));
                    }

                    String accessToken = jwtTokenService.generateAccessToken(client.getId().getValue());
                    String refreshToken = jwtTokenService.generateRefreshToken(client.getId().getValue());

                    client.authenticate(accessToken, refreshToken);

                    return clientRepository.save(client)
                            .map(savedClient -> new Result(
                                    savedClient.getId().getValue(),
                                    accessToken,
                                    refreshToken,
                                    savedClient.getStatus().name()
                            ));
                });
    }

    public record Command(String phone, String otp) {}

    public record Result(String clientId, String accessToken, String refreshToken, String status) {}
}