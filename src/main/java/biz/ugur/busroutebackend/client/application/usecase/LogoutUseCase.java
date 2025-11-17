package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service("clientLogoutUseCase")
public class LogoutUseCase extends BaseUseCase<Mono<LogoutUseCase.Command>, LogoutUseCase.Result> {

    private final ClientRepository clientRepository;

    public LogoutUseCase(ClientRepository clientRepository,
                         CorrelationContextService correlationContextService,
                         EventBus eventBus) {
        super(correlationContextService, eventBus);
        this.clientRepository = clientRepository;
    }

    @Override
    protected Mono<Result> process(Mono<Command> command) {
        return command.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "client";
    }

    private Mono<Result> processInternal(Command cmd) {
        return correlationService.getCurrentCorrelationId()
            .flatMap(correlationId -> {
                log.info("[Logout] CorrelationId: {} - Logging out client: {}", correlationId, cmd.clientId());

                return clientRepository.findById(ClientId.of(cmd.clientId()))
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Client not found")))
                    .flatMap(client -> {
                        log.debug("[Logout] Client found: {}, status: {}", cmd.clientId(), client.getStatus());

                        client.logout();

                        return clientRepository.save(client)
                            .doOnSuccess(saved ->
                                log.info("[Logout] Client logged out successfully: {}", cmd.clientId()))
                            .map(saved -> new Result(
                                saved.getId().getValue(),
                                true,
                                "Logout successful"
                            ));
                    })
                    .onErrorResume(error -> {
                        log.error("[Logout] Error logging out client {}: {}", cmd.clientId(), error.getMessage());
                        return Mono.error(new IllegalArgumentException("Logout failed: " + error.getMessage()));
                    });
            });
    }

    public record Command(String clientId) {}

    public record Result(String clientId, boolean success, String message) {}
}
