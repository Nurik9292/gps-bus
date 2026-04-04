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
                    .switchIfEmpty(Mono.error(
                            IntegrationClientNotFoundException.byExternalUserId(serviceId, request.externalUserId())
                    ));
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

    private Mono<IntegrationClientTokenResponse> generateTokens(Client client,
                                                                 ExternalService service) {
        String clientId = client.getId().getValue();

        return Mono.zip(
                clientJwtTokenService.generateAccessToken(clientId),
                clientJwtTokenService.generateRefreshToken(clientId)
        ).flatMap(tokens -> {
            client.authenticate(tokens.getT1(), tokens.getT2());

            return Mono.zip(
                    clientRepository.save(client),
                    externalServiceRepository.save(service.recordUsage())
            ).map(saved -> IntegrationClientTokenResponse.builder()
                    .clientId(clientId)
                    .externalUserId(client.getExternalUserId())
                    .accessToken(tokens.getT1())
                    .refreshToken(tokens.getT2())
                    .expiresIn(ACCESS_TOKEN_EXPIRATION_SECONDS)
                    .tokenType("Bearer")
                    .build());
        });
    }
}
