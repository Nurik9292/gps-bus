package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.enums.Platform;
import biz.ugur.busroutebackend.client.domain.model.Client;
import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class RegisterClientUseCase implements UseCase<RegisterClientUseCase.Command, Mono<RegisterClientUseCase.Result>> {

    private final ClientRepository clientRepository;

    @Override
    public Mono<Result> execute(Command command) {
        return clientRepository.existsByPhone(command.phone())
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException("Phone number already registered"));
                    }

                    Client client = new Client(command.name(), command.phone(), command.platform());
                    client.generateOtp();

                    return clientRepository.save(client)
                            .map(savedClient -> new Result(
                                    savedClient.getId().getValue(),
                                    savedClient.getName(),
                                    savedClient.getPhoneNumber(),
                                    savedClient.getOtpCode(),
                                    savedClient.getStatus().name()
                            ));
                });
    }

    public record Command(String name, String phone, Platform platform) {}

    public record Result(String clientId, String name, String phone, String otpCode, String status) {}
}