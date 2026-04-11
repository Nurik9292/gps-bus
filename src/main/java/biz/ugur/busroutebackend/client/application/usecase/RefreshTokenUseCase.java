package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.infrastructure.security.ClientJwtTokenService;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service("clientRefreshTokenUseCase")
public class RefreshTokenUseCase extends BaseUseCase<Mono<RefreshTokenUseCase.Command>, RefreshTokenUseCase.Result> {

    private final ClientRepository clientRepository;
    private final ClientJwtTokenService jwtTokenService;

    public RefreshTokenUseCase(ClientRepository clientRepository,
                               ClientJwtTokenService jwtTokenService,
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

    private Mono<Result> processInternal(Command cmd) {
        return correlationService.getCurrentCorrelationId()
            .flatMap(correlationId -> {
                log.info("[RefreshToken] CorrelationId: {} - Refreshing token", correlationId);

                return jwtTokenService.validateRefreshToken(cmd.refreshToken())
                    .flatMap(clientId -> {
                        log.debug("[RefreshToken] Token validated for clientId: {}", clientId);

                        return clientRepository.findById(ClientId.of(clientId))
                            .switchIfEmpty(Mono.error(new IllegalArgumentException("Client not found")))
                            .flatMap(client -> {

                                if (!client.isRefreshTokenValid(cmd.refreshToken())) {
                                    log.warn("[RefreshToken] Invalid refresh token for client: {}", clientId);
                                    return Mono.error(new IllegalArgumentException("Invalid refresh token"));
                                }

                                return Mono.zip(
                                    jwtTokenService.generateAccessToken(clientId),
                                    jwtTokenService.generateRefreshToken(clientId)
                                ).flatMap(tokens -> {
                                    String newAccessToken = tokens.getT1();
                                    String newRefreshToken = tokens.getT2();

                                    client.updateTokens(newAccessToken, newRefreshToken);

                                    return clientRepository.save(client)
                                        .doOnSuccess(saved ->
                                            log.info("[RefreshToken] Tokens refreshed successfully for client: {}", clientId))
                                        .map(saved -> new Result(
                                            saved.getId().getValue(),
                                            newAccessToken,
                                            newRefreshToken
                                        ));
                                });
                            });
                    })
                    .onErrorResume(error -> {
                        log.error("[RefreshToken] Error refreshing token: {}", error.getMessage());
                        return Mono.error(new IllegalArgumentException("Token refresh failed: " + error.getMessage()));
                    });
            });
    }

    public record Command(String refreshToken) {}

    public record Result(String clientId, String accessToken, String refreshToken) {}
}
