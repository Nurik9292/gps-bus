package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.client.infrastructure.security.ClientJwtTokenService;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Log4j2
@Service
public class AuthenticateClientUseCase extends BaseUseCase<Mono<AuthenticateClientUseCase.Command>, AuthenticateClientUseCase.Result> {

    private final ClientRepository clientRepository;
    private final ClientJwtTokenService clientJwtTokenService;

    public AuthenticateClientUseCase(ClientRepository clientRepository,
                                     ClientJwtTokenService clientJwtTokenService,
                                     CorrelationContextService correlationContextService,
                                     EventBus eventBus) {
        super(correlationContextService, eventBus);
        this.clientRepository = clientRepository;
        this.clientJwtTokenService = clientJwtTokenService;
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
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Authenticate client CorrelationId: {} - phone: {}", correlationId, command.phone());

            return clientRepository.findByPhone(command.phone())
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Client not found")))
                    .flatMap(client -> {
                        if (!client.isOtpVerified()) {
                            return Mono.error(new IllegalArgumentException("Phone not verified"));
                        }

                        if (!client.isActive()) {
                            return Mono.error(new IllegalArgumentException("Client account not active"));
                        }

                        return Mono.zip(
                                clientJwtTokenService.generateAccessToken(client.getId().getValue()),
                                clientJwtTokenService.generateRefreshToken(client.getId().getValue())
                        ).flatMap(tokens -> {
                            client.authenticate(tokens.getT1(), tokens.getT2());

                            return clientRepository.save(client)
                                    .map(savedClient -> new Result(
                                            savedClient.getId().getValue(),
                                            tokens.getT1(),
                                            tokens.getT2(),
                                            savedClient.getStatus().name()
                                    ));
                        });
                    });
        });
    }

    public record Command(String phone, String otp) {}

    public record Result(String clientId, String accessToken, String refreshToken, String status) {}
}