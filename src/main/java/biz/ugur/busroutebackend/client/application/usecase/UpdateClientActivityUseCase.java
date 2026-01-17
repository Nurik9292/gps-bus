package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

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

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(50);

    private Mono<Void> processInternal(Command command) {
        ClientId clientId = ClientId.of(command.clientId());

        return Mono.defer(() ->
                clientRepository.findById(clientId)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Client not found")))
                        .flatMap(client -> {
                            client.updateActivity();
                            return clientRepository.save(client);
                        })
        )
        .retryWhen(Retry.backoff(MAX_RETRY_ATTEMPTS, RETRY_BACKOFF)
                .filter(e -> e instanceof OptimisticLockingFailureException))
        .then();
    }

    public record Command(String clientId) {}
}
