package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UpdateClientActivityUseCase implements UseCase<UpdateClientActivityUseCase.Command, Mono<Void>> {

    private final ClientRepository clientRepository;

    @Override
    public Mono<Void> execute(Command command) {
        return clientRepository.findById(ClientId.of(command.clientId()))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Client not found")))
                .flatMap(client -> {
                    client.updateActivity();
                    return clientRepository.save(client);
                })
                .then();
    }

    public record Command(String clientId) {}
}
