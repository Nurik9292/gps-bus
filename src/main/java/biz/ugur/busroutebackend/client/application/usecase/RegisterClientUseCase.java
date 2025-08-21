package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.enums.Platform;
import biz.ugur.busroutebackend.client.domain.model.Client;
import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Log4j2
@Service
public class RegisterClientUseCase extends BaseUseCase<Mono<RegisterClientUseCase.Command>, RegisterClientUseCase.Result> {

    private final ClientRepository clientRepository;

    public RegisterClientUseCase(ClientRepository clientRepository,
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

    private Mono<Result> processInternal(Command command) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Register client CorrelationId: {} - Client Phone: {}", correlationId, command.phone());

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
        });
    }

    public record Command(String name, String phone, Platform platform) {}

    public record Result(String clientId, String name, String phone, String otpCode, String status) {}
}