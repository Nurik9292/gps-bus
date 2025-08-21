package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class VerifyOtpUseCase extends BaseUseCase<Mono<VerifyOtpUseCase.Command>, VerifyOtpUseCase.Result> {

    private final ClientRepository clientRepository;

    protected VerifyOtpUseCase(CorrelationContextService correlationService,
                               EventBus eventBus,
                               ClientRepository clientRepository) {
        super(correlationService, eventBus);
        this.clientRepository = clientRepository;
    }


    @Override
    protected Mono<Result> process(Mono<Command> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "client";
    }

    private Mono<Result> processInternal(Command command) {
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
