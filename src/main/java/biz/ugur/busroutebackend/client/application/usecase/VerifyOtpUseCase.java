package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Log4j2
@Service
public class VerifyOtpUseCase extends BaseUseCase<Mono<VerifyOtpUseCase.Command>, VerifyOtpUseCase.Result> {

    private final ClientRepository clientRepository;

    public VerifyOtpUseCase(ClientRepository clientRepository,
                            CorrelationContextService correlationContextService,
                            EventBus  eventBus) {
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
            log.info("Client verification otp CorrelationId: {} - Client Phone: {}", correlationId, command.phone());

            return clientRepository.findByPhone(command.phone())
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Client not found")))
                    .flatMap(client -> {
                        boolean verified = client.verifyOtp(command.otp());
                        if (!verified) {
                            return Mono.error(new IllegalArgumentException("Invalid OTP"));
                        }
                        System.out.println("Test " + client);
                        return clientRepository.save(client)
                                .map(savedClient -> new Result(
                                        savedClient.getId().getValue(),
                                        savedClient.isOtpVerified(),
                                        savedClient.getStatus().name()
                                ));
                    });
        });
    }

    public record Command(String phone, String otp) {}

    public record Result(String clientId, boolean verified, String status) {}
}
