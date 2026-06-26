package biz.ugur.busroutebackend.integration.application.usecase;

import biz.ugur.busroutebackend.client.domain.model.Client;
import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.domain.valueobject.Phone;
import biz.ugur.busroutebackend.client.infrastructure.security.ClientJwtProperties;
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
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;


@Service
@RequiredArgsConstructor
@Slf4j
public class GetIntegrationClientTokenUseCase {

    private static final int MAX_TOKEN_ROTATION_RETRIES = 8;
    private static final Duration RETRY_BACKOFF = Duration.ofMillis(50);

    private final ExternalServiceRepository externalServiceRepository;
    private final ClientRepository clientRepository;
    private final ClientJwtTokenService clientJwtTokenService;
    private final ClientJwtProperties clientJwtProperties;

    public Mono<IntegrationClientTokenResponse> execute(String serviceId,
                                                         IntegrationClientTokenRequest request) {
        log.info("Getting client token for service: {}, clientId: {}, externalUserId: {}",
                serviceId, request.clientId(), request.externalUserId());

        return externalServiceRepository.findById(ExternalServiceId.of(serviceId))
                .switchIfEmpty(Mono.error(new ExternalServiceNotFoundException(ExternalServiceId.of(serviceId))))
                .flatMap(service -> {
                    service.validateClientManagement();
                    return Mono.defer(() -> findAndValidateClient(service, request)
                                    .flatMap(client -> generateTokens(client, service)))
                            .retryWhen(Retry.backoff(MAX_TOKEN_ROTATION_RETRIES, RETRY_BACKOFF)
                                    .filter(e -> e instanceof OptimisticLockingFailureException)
                                    .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                            .onErrorResume(OptimisticLockingFailureException.class,
                                    contention -> reuseExistingAfterContention(service, request, contention));
                })
                .doOnSuccess(response -> log.info("Successfully generated token for client: {}",
                        response.clientId()))
                .doOnError(error -> {
                    if (error instanceof IntegrationClientNotFoundException
                            || error instanceof ClientNotBelongsToServiceException) {
                        log.warn("[INTEGRATION] token request failed for service={}: {}",
                                serviceId, error.getMessage());
                    } else {
                        log.error("Error generating token for service: {}", serviceId, error);
                    }
                });
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
            String externalUserId = request.externalUserId();
            String canonicalPhone = canonicalizeOrNull(request.phone());
            clientMono = clientRepository.findByServiceAndExternalUserId(serviceId, externalUserId)
                    .flatMap(existing -> upgradePhoneIfSynthetic(existing, canonicalPhone))
                    .switchIfEmpty(Mono.defer(() -> resolveByPhoneOrCreate(serviceId, externalUserId, canonicalPhone)));
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

    private String canonicalizeOrNull(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return null;
        }
        try {
            return Phone.canonicalWithoutPlus(rawPhone);
        } catch (RuntimeException invalid) {
            log.warn("[INTEGRATION] invalid phone '{}' — keeping synthetic identity", rawPhone);
            return null;
        }
    }

    private boolean isSyntheticPhone(String phone) {
        return phone != null && phone.startsWith("+993INT");
    }

    private Mono<Client> upgradePhoneIfSynthetic(Client existing, String canonicalPhone) {
        if (canonicalPhone == null || !isSyntheticPhone(existing.getPhoneNumber())) {
            return Mono.just(existing);
        }
        return clientRepository.findByPhone(canonicalPhone)
                .flatMap(other -> {
                    log.warn("[INTEGRATION] real phone already taken by clientId={} — keeping synthetic for externalUserId={}",
                            other.getId().getValue(), existing.getExternalUserId());
                    return Mono.just(existing);
                })
                .switchIfEmpty(Mono.defer(() ->
                        clientRepository.save(existing.upgradeIntegrationPhone(canonicalPhone))
                                .onErrorResume(DuplicateKeyException.class, race -> {
                                    log.warn("[INTEGRATION] race upgrading phone for externalUserId={} — keeping synthetic",
                                            existing.getExternalUserId());
                                    return Mono.just(existing);
                                })));
    }

    private Mono<Client> resolveByPhoneOrCreate(String serviceId, String externalUserId, String canonicalPhone) {
        if (canonicalPhone == null) {
            return autoCreate(serviceId, externalUserId, null);
        }
        return clientRepository.findByPhone(canonicalPhone)
                .flatMap(existing -> mergeOrFallback(existing, serviceId, externalUserId))
                .switchIfEmpty(Mono.defer(() -> autoCreate(serviceId, externalUserId, canonicalPhone)));
    }

    private Mono<Client> mergeOrFallback(Client existing, String serviceId, String externalUserId) {
        if (existing.getExternalUserId() == null) {
            log.info("[INTEGRATION] merging externalUserId={} into existing clientId={} matched by phone",
                    externalUserId, existing.getId().getValue());
            return clientRepository.save(existing.linkExternalService(serviceId, externalUserId));
        }
        if (externalUserId.equals(existing.getExternalUserId())) {
            return Mono.just(existing);
        }
        log.warn("[INTEGRATION] phone of externalUserId={} already belongs to externalUserId={} — creating separate client",
                externalUserId, existing.getExternalUserId());
        return autoCreate(serviceId, externalUserId, null);
    }

    private Mono<Client> autoCreate(String serviceId, String externalUserId, String canonicalPhone) {
        Client newClient = Client.createViaExternalService(
                "User " + externalUserId,
                serviceId,
                externalUserId,
                canonicalPhone);
        return clientRepository.save(newClient)
                .onErrorResume(DuplicateKeyException.class,
                        dup -> recoverFromDuplicate(serviceId, externalUserId, canonicalPhone, dup))
                .doOnSuccess(saved -> log.info(
                        "[INTEGRATION] auto-registered client for service={} externalUserId={} clientId={}",
                        serviceId, externalUserId, saved.getId().getValue()));
    }

    private Mono<Client> recoverFromDuplicate(String serviceId, String externalUserId,
                                              String canonicalPhone, DuplicateKeyException dup) {
        return clientRepository.findByServiceAndExternalUserId(serviceId, externalUserId)
                .switchIfEmpty(Mono.defer(() -> {
                    if (canonicalPhone == null) {
                        return Mono.error(dup);
                    }
                    return clientRepository.findByPhone(canonicalPhone)
                            .flatMap(existing -> existing.getExternalUserId() == null
                                    ? clientRepository.save(existing.linkExternalService(serviceId, externalUserId))
                                    : Mono.just(existing))
                            .switchIfEmpty(Mono.error(dup));
                }))
                .doOnNext(reused -> log.info(
                        "[INTEGRATION] recovered from duplicate-key for service={} externalUserId={} — reusing clientId={}",
                        serviceId, externalUserId, reused.getId().getValue()));
    }

    private Mono<IntegrationClientTokenResponse> generateTokens(Client client,
                                                                 ExternalService service) {
        return existingValidTokenPair(client)
                .doOnNext(reused -> log.debug("Reusing existing valid tokens for client {}",
                        client.getId().getValue()))
                .switchIfEmpty(Mono.defer(() -> rotateAndPersist(client)))
                .map(tokens -> {
                    recordUsageBestEffort(service)
                            .subscribe(ignored -> { },
                                    err -> log.debug("[INTEGRATION] detached usage record failed for service={}: {}",
                                            service.getId().getValue(), err.getMessage()));
                    return buildResponse(client, tokens);
                });
    }

    private Mono<IntegrationClientTokenResponse> reuseExistingAfterContention(
            ExternalService service,
            IntegrationClientTokenRequest request,
            OptimisticLockingFailureException contention) {
        log.warn("[INTEGRATION] token rotation contended for service={} — reusing concurrently written token",
                service.getId().getValue());
        return findAndValidateClient(service, request)
                .flatMap(client -> existingValidTokenPair(client)
                        .map(tokens -> buildResponse(client, tokens)))
                .switchIfEmpty(Mono.error(contention));
    }

    private Mono<Void> recordUsageBestEffort(ExternalService service) {
        return externalServiceRepository.save(service.recordUsage())
                .onErrorResume(DataAccessException.class, e -> {
                    log.debug("[INTEGRATION] usage counter update skipped for service={}: {}",
                            service.getId().getValue(), e.getMessage());
                    return Mono.empty();
                })
                .then();
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
                .expiresIn(clientJwtProperties.getAccessTokenExpiration().toSeconds())
                .tokenType("Bearer")
                .build();
    }

    private record TokenPair(String access, String refresh) {}
}
