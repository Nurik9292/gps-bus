package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class VerifyOtpUseCase implements UseCase<VerifyOtpUseCase.Command, Mono<VerifyOtpUseCase.Result>> {

    private final ClientRepository clientRepository;

    @Override
    public Mono<Result> execute(Command command) {
        return clientRepository.findByPhone(command.phone())
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Client not found")))
                .flatMap(client -> {
                    boolean verified = client.verifyOtp(command.otp());
                    if (!verified) {
                        return Mono.error(new IllegalArgumentException("Invalid OTP"));
                    }
                    return clientRepository.save(client)
                            .map(savedClient -> new Result(
                                    savedClient.getId().getValue(),
                                    savedClient.isOtpVerified(),
                                    savedClient.getStatus().name()
                            ));
                });
    }

    public record Command(String phone, String otp) {}

    public record Result(String clientId, boolean verified, String status) {}
}
