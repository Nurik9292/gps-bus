package biz.ugur.busroutebackend.integration.application.usecase;

import biz.ugur.busroutebackend.client.domain.model.Client;
import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.infrastructure.security.ClientJwtTokenService;
import biz.ugur.busroutebackend.integration.application.dto.IntegrationClientTokenRequest;
import biz.ugur.busroutebackend.integration.application.dto.IntegrationClientTokenResponse;
import biz.ugur.busroutebackend.integration.domain.exceptions.ClientNotBelongsToServiceException;
import biz.ugur.busroutebackend.integration.domain.exceptions.ExternalServiceNotFoundException;
import biz.ugur.busroutebackend.integration.domain.exceptions.IntegrationClientNotFoundException;
import biz.ugur.busroutebackend.integration.domain.model.ExternalService;
import biz.ugur.busroutebackend.integration.domain.repository.ExternalServiceRepository;
import biz.ugur.busroutebackend.integration.domain.valueobjects.ExternalServiceId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;


@Service
@RequiredArgsConstructor
@Slf4j
public class GetIntegrationClientTokenUseCase {

    private static final Long ACCESS_TOKEN_EXPIRATION_SECONDS = 2678400L;

    private final ExternalServiceRepository externalServiceRepository;
    private final ClientRepository clientRepository;
    private final ClientJwtTokenService clientJwtTokenService;

    public Mono<IntegrationClientTokenResponse> execute(String serviceId,
                                                         IntegrationClientTokenRequest request) {
        log.info("Getting client token for service: {}, clientId: {}, externalUserId: {}",
                serviceId, request.clientId(), request.externalUserId());

        return externalServiceRepository.findById(ExternalServiceId.of(serviceId))
                .switchIfEmpty(Mono.error(new ExternalServiceNotFoundException(ExternalServiceId.of(serviceId))))
                .flatMap(service -> {
                    service.validateClientManagement();
                    return findAndValidateClient(service, request)
                            .flatMap(client -> generateTokens(client, service));
                })
                .doOnSuccess(response -> log.info("Successfully generated token for client: {}",
                        response.clientId()))
                .doOnError(error -> log.error("Error generating token for service: {}", serviceId, error));
    }

    private Mono<Client> findAndValidateClient(ExternalService service,
                                                IntegrationClientTokenRequest request) {
        String serviceId = service.getId().getValue();

        Mono<Client> clientMono;

        if (request.clientId() != null && !request.clientId().isBlank()) {
            clientMono = clientRepository.findById(ClientId.of(request.clientId()))
                    .switchIfEmpty(Mono.error(
                            IntegrationClientNotFoundException.byClientId(request.clientId())
                    ));
        } else if (request.externalUserId() != null && !request.externalUserId().isBlank()) {
            clientMono = clientRepository.findByServiceAndExternalUserId(serviceId, request.externalUserId())
                    .switchIfEmpty(Mono.defer(() -> autoCreate(service, request.externalUserId())));
        } else {
            return Mono.error(new IllegalArgumentException(
                    "Either clientId or externalUserId must be provided"
            ));
        }

        return clientMono.flatMap(client -> {
            if (!client.belongsToService(serviceId)) {
                return Mono.error(new ClientNotBelongsToServiceException(
                        client.getId().getValue(),
                        serviceId
                ));
            }
            if (!client.isActive()) {
                return Mono.error(new IllegalStateException("Client is not active"));
            }
            return Mono.just(client);
        });
    }

    private Mono<Client> autoCreate(ExternalService service, String externalUserId) {
        String serviceId = service.getId().getValue();
        Client newClient = Client.createViaExternalService(
                "User " + externalUserId,
                serviceId,
                externalUserId);
        return clientRepository.save(newClient)
                .doOnSuccess(saved -> log.info(
                        "[INTEGRATION] auto-registered client for service={} externalUserId={} clientId={}",
                        serviceId, externalUserId, saved.getId().getValue()));
    }

    private Mono<IntegrationClientTokenResponse> generateTokens(Client client,
                                                                 ExternalService service) {
        return existingValidTokenPair(client)
                .doOnNext(reused -> log.debug("Reusing existing valid tokens for client {}",
                        client.getId().getValue()))
                .switchIfEmpty(Mono.defer(() -> rotateAndPersist(client)))
                .flatMap(tokens -> externalServiceRepository.save(service.recordUsage())
                        .thenReturn(buildResponse(client, tokens)));
    }

    private Mono<TokenPair> existingValidTokenPair(Client client) {
        String access = client.getAccessToken();
        String refresh = client.getRefreshToken();
        if (access == null || access.isBlank() || refresh == null || refresh.isBlank()) {
            return Mono.empty();
        }
        return clientJwtTokenService.isTokenExpired(access)
                .flatMap(expired -> expired
                        ? Mono.empty()
                        : Mono.just(new TokenPair(access, refresh)));
    }

    private Mono<TokenPair> rotateAndPersist(Client client) {
        String clientId = client.getId().getValue();
        return Mono.zip(
                clientJwtTokenService.generateAccessToken(clientId),
                clientJwtTokenService.generateRefreshToken(clientId)
        ).flatMap(generated -> {
            client.authenticate(generated.getT1(), generated.getT2());
            return clientRepository.save(client)
                    .thenReturn(new TokenPair(generated.getT1(), generated.getT2()));
        });
    }

    private IntegrationClientTokenResponse buildResponse(Client client, TokenPair tokens) {
        return IntegrationClientTokenResponse.builder()
                .clientId(client.getId().getValue())
                .externalUserId(client.getExternalUserId())
                .accessToken(tokens.access())
                .refreshToken(tokens.refresh())
                .expiresIn(ACCESS_TOKEN_EXPIRATION_SECONDS)
                .tokenType("Bearer")
                .build();
    }

    private record TokenPair(String access, String refresh) {}
}
