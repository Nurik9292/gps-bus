package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Log4j2
@Service
public class UpdateClientActivityUseCase extends BaseUseCase<Mono<UpdateClientActivityUseCase.Command>, Void> {

    private final ClientRepository clientRepository;

    public UpdateClientActivityUseCase(ClientRepository clientRepository,
                                       CorrelationContextService contextService,
                                       EventBus eventBus) {
        super(contextService, eventBus);
        this.clientRepository = clientRepository;
    }

    @Override
    protected Mono<Void> process(Mono<Command> command) {
        return command.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "client";
    }

    private Mono<Void> processInternal(Command command) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Update status client CorrelationId: {} - ClientId: {} ", correlationId, command.clientId());

            return clientRepository.findById(ClientId.of(command.clientId()))
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Client not found")))
                    .flatMap(client -> {
                        client.updateActivity();
                        return clientRepository.save(client);
                    })
                    .then();
        });
    }

    public record Command(String clientId) {}
}
