package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.enums.Platform;
import biz.ugur.busroutebackend.client.domain.model.Client;
import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.client.infrastructure.security.JwtTokenService;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Log4j2
public class CenterRegisterClientUseCase extends
        BaseUseCase<Mono<CenterRegisterClientUseCase.Command>, CenterRegisterClientUseCase.Result> {

    private final ClientRepository clientRepository;
    private final JwtTokenService jwtTokenService;

    public CenterRegisterClientUseCase(ClientRepository clientRepository,
                                       JwtTokenService jwtTokenService,
                                       CorrelationContextService correlationContextService,
                                       EventBus eventBus) {
        super(correlationContextService, eventBus);
        this.clientRepository = clientRepository;
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    protected Mono<Result> process(Mono<Command> command) {
        return command.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "client";
    }


    private Mono<Result> processInternal(Command command) {
        return correlationService.getCurrentCorrelationId()
                .doOnNext(correlationId ->
                        log.info("Center auth client Correlation Id: {} client phone: {}", correlationId, command.phone))
                .flatMap(id -> findOrCreateClient(command)
                        .flatMap(clientRepository::save)
                        .map(this::buildResult));
    }

    private Mono<Client> findOrCreateClient(Command command) {
        return clientRepository.findByPhone(command.phone)
                .map(this::authenticateExistingClient)
                .switchIfEmpty(Mono.defer(() -> createNewClient(command)));
    }

    private Client authenticateExistingClient(Client client) {
        String accessToken = jwtTokenService.generateAccessToken(client.getId().getValue());
        String refreshToken = jwtTokenService.generateRefreshToken(client.getId().getValue());
        client.authenticate(accessToken, refreshToken);
        return client;
    }

    private Mono<Client> createNewClient(Command command) {
        Client client = new Client("Center", command.phone, Platform.MOBILE_WEB);
        client.generateOtpForCenter();
        client.verifyOtpCenter(client.getOtpCode());
        return Mono.just(authenticateExistingClient(client));
    }

    private Result buildResult(Client savedClient) {
        return new Result(
                savedClient.getId().getValue(),
                savedClient.getAccessToken(),
                savedClient.getRefreshToken(),
                savedClient.getStatus().name()
        );
    }

    public record Command(String phone) {}

    public record Result(String clientId, String accessToken, String refreshToken, String status) {}
}
