package biz.ugur.busroutebackend.integration.application.usecase;

import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.client.domain.enums.ClientStatus;
import biz.ugur.busroutebackend.client.domain.enums.Platform;
import biz.ugur.busroutebackend.client.domain.model.Client;
import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.client.infrastructure.security.ClientJwtProperties;
import biz.ugur.busroutebackend.client.infrastructure.security.ClientJwtTokenService;
import biz.ugur.busroutebackend.integration.application.dto.IntegrationClientTokenRequest;
import biz.ugur.busroutebackend.integration.domain.exceptions.ClientManagementNotAllowedException;
import biz.ugur.busroutebackend.integration.domain.model.ExternalService;
import biz.ugur.busroutebackend.integration.domain.repository.ExternalServiceRepository;
import biz.ugur.busroutebackend.integration.domain.valueobjects.ExternalServiceId;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetIntegrationClientTokenUseCaseTest {

    private static final String SERVICE_NAME = "PartnerSvc";
    private static final String EXTERNAL_USER_ID = "1980";
    private static final String EXISTING_ACCESS_TOKEN  = "existing-access-jwt";
    private static final String EXISTING_REFRESH_TOKEN = "existing-refresh-jwt";
    private static final String NEW_ACCESS_TOKEN  = "fresh-access-jwt";
    private static final String NEW_REFRESH_TOKEN = "fresh-refresh-jwt";
    private static final String REAL_PHONE_8 = "61520000";
    private static final String CANON_PHONE  = "99361520000";

    @InjectMocks
    private GetIntegrationClientTokenUseCase useCase;

    @Mock
    private ExternalServiceRepository externalServiceRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ClientJwtTokenService clientJwtTokenService;

    @Mock
    private ClientJwtProperties clientJwtProperties;

    private ExternalService service;

    @BeforeEach
    void setUp() {
        service = ExternalService.create(
                SERVICE_NAME,
                "test partner",
                AdminId.generate(),
                List.of("/api/v1/integration/**"),
                60,
                true
        );
        lenient().when(clientJwtProperties.getAccessTokenExpiration())
                .thenReturn(Duration.ofDays(31));
    }

    private Client clientWithTokens(String access, String refresh) {
        return Client.fromDatabase(
                ClientId.generate(),
                "Имя",
                "+99312000001",
                null,
                true,
                Platform.API,
                ClientStatus.ACTIVE,
                LocalDateTime.now(),
                access,
                refresh,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().minusDays(1),
                1L,
                service.getId().getValue(),
                EXTERNAL_USER_ID
        );
    }

    private IntegrationClientTokenRequest byExternalUser() {
        return new IntegrationClientTokenRequest(null, EXTERNAL_USER_ID, null);
    }

    private IntegrationClientTokenRequest byExternalUserWithPhone(String phone) {
        return new IntegrationClientTokenRequest(null, EXTERNAL_USER_ID, phone);
    }

    private Client directClient(String phone, String access, String refresh) {
        return Client.fromDatabase(
                ClientId.generate(),
                "Прямой",
                phone,
                null,
                true,
                Platform.ANDROID,
                ClientStatus.ACTIVE,
                LocalDateTime.now(),
                access,
                refresh,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().minusDays(1),
                1L,
                null,
                null
        );
    }

    private Client syntheticIntegrationClient(String access, String refresh) {
        return Client.fromDatabase(
                ClientId.generate(),
                "User " + EXTERNAL_USER_ID,
                "+993INTABCDEF_12345678",
                null,
                true,
                Platform.API,
                ClientStatus.ACTIVE,
                LocalDateTime.now(),
                access,
                refresh,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().minusDays(1),
                1L,
                service.getId().getValue(),
                EXTERNAL_USER_ID
        );
    }

    @Test
    void reusesExistingTokensWhenAccessTokenStillValid() {
        Client client = clientWithTokens(EXISTING_ACCESS_TOKEN, EXISTING_REFRESH_TOKEN);

        when(externalServiceRepository.findById(service.getId())).thenReturn(Mono.just(service));
        when(clientRepository.findByServiceAndExternalUserId(service.getId().getValue(), EXTERNAL_USER_ID))
                .thenReturn(Mono.just(client));
        when(clientJwtTokenService.isTokenExpired(EXISTING_ACCESS_TOKEN))
                .thenReturn(Mono.just(false));
        when(externalServiceRepository.save(any(ExternalService.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(service.getId().getValue(), byExternalUser()))
                .assertNext(response -> {
                    assertEquals(EXISTING_ACCESS_TOKEN,  response.accessToken());
                    assertEquals(EXISTING_REFRESH_TOKEN, response.refreshToken());
                    assertEquals(EXTERNAL_USER_ID, response.externalUserId());
                })
                .verifyComplete();

        verify(clientRepository, never()).save(any(Client.class));
        verify(clientJwtTokenService, never()).generateAccessToken(any());
        verify(clientJwtTokenService, never()).generateRefreshToken(any());
        verify(externalServiceRepository, times(1)).save(any(ExternalService.class));
    }

    @Test
    void rotatesTokensWhenAccessTokenExpired() {
        Client client = clientWithTokens(EXISTING_ACCESS_TOKEN, EXISTING_REFRESH_TOKEN);

        when(externalServiceRepository.findById(service.getId())).thenReturn(Mono.just(service));
        when(clientRepository.findByServiceAndExternalUserId(service.getId().getValue(), EXTERNAL_USER_ID))
                .thenReturn(Mono.just(client));
        when(clientJwtTokenService.isTokenExpired(EXISTING_ACCESS_TOKEN))
                .thenReturn(Mono.just(true));
        when(clientJwtTokenService.generateAccessToken(any())).thenReturn(Mono.just(NEW_ACCESS_TOKEN));
        when(clientJwtTokenService.generateRefreshToken(any())).thenReturn(Mono.just(NEW_REFRESH_TOKEN));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(externalServiceRepository.save(any(ExternalService.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(service.getId().getValue(), byExternalUser()))
                .assertNext(response -> {
                    assertEquals(NEW_ACCESS_TOKEN,  response.accessToken());
                    assertEquals(NEW_REFRESH_TOKEN, response.refreshToken());
                })
                .verifyComplete();

        verify(clientRepository, times(1)).save(any(Client.class));
    }

    @Test
    void rotatesTokensWhenAccessTokenIsBlankOnClient() {
        Client client = clientWithTokens(null, null);

        when(externalServiceRepository.findById(service.getId())).thenReturn(Mono.just(service));
        when(clientRepository.findByServiceAndExternalUserId(service.getId().getValue(), EXTERNAL_USER_ID))
                .thenReturn(Mono.just(client));
        when(clientJwtTokenService.generateAccessToken(any())).thenReturn(Mono.just(NEW_ACCESS_TOKEN));
        when(clientJwtTokenService.generateRefreshToken(any())).thenReturn(Mono.just(NEW_REFRESH_TOKEN));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(externalServiceRepository.save(any(ExternalService.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(service.getId().getValue(), byExternalUser()))
                .assertNext(response -> assertEquals(NEW_ACCESS_TOKEN, response.accessToken()))
                .verifyComplete();

        verify(clientJwtTokenService, never()).isTokenExpired(any());
        verify(clientRepository, times(1)).save(any(Client.class));
    }

    @Test
    void autoCreatesClientWhenNotFoundByExternalUserId() {
        when(externalServiceRepository.findById(service.getId())).thenReturn(Mono.just(service));
        when(clientRepository.findByServiceAndExternalUserId(service.getId().getValue(), EXTERNAL_USER_ID))
                .thenReturn(Mono.empty());
        when(clientRepository.save(any(Client.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(clientJwtTokenService.generateAccessToken(any())).thenReturn(Mono.just(NEW_ACCESS_TOKEN));
        when(clientJwtTokenService.generateRefreshToken(any())).thenReturn(Mono.just(NEW_REFRESH_TOKEN));
        when(externalServiceRepository.save(any(ExternalService.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(service.getId().getValue(), byExternalUser()))
                .assertNext(response -> {
                    assertEquals(NEW_ACCESS_TOKEN, response.accessToken());
                    assertEquals(NEW_REFRESH_TOKEN, response.refreshToken());
                    assertEquals(EXTERNAL_USER_ID, response.externalUserId());
                })
                .verifyComplete();

        ArgumentCaptor<Client> savedClient = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository, org.mockito.Mockito.atLeastOnce()).save(savedClient.capture());
        Client created = savedClient.getAllValues().get(0);
        Assertions.assertEquals("User " + EXTERNAL_USER_ID, created.getName());
        Assertions.assertEquals(EXTERNAL_USER_ID, created.getExternalUserId());
        Assertions.assertEquals(Platform.API, created.getPlatform());
    }

    @Test
    void autoCreateConcurrentDuplicateKeyRefetchesExistingClient() {
        Client concurrentlyCreated = clientWithTokens(EXISTING_ACCESS_TOKEN, EXISTING_REFRESH_TOKEN);

        when(externalServiceRepository.findById(service.getId())).thenReturn(Mono.just(service));
        when(clientRepository.findByServiceAndExternalUserId(service.getId().getValue(), EXTERNAL_USER_ID))
                .thenReturn(Mono.empty())
                .thenReturn(Mono.just(concurrentlyCreated));
        when(clientRepository.save(any(Client.class)))
                .thenReturn(Mono.error(new DuplicateKeyException("duplicate external_user_id")));
        when(clientJwtTokenService.isTokenExpired(EXISTING_ACCESS_TOKEN)).thenReturn(Mono.just(false));
        when(externalServiceRepository.save(any(ExternalService.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(service.getId().getValue(), byExternalUser()))
                .assertNext(response -> assertEquals(EXISTING_ACCESS_TOKEN, response.accessToken()))
                .verifyComplete();
    }

    @Test
    void rotateRetriesOnOptimisticLockThenReusesConcurrentlyWrittenTokens() {
        Client stale = clientWithTokens(null, null);
        Client refreshed = clientWithTokens(EXISTING_ACCESS_TOKEN, EXISTING_REFRESH_TOKEN);

        when(externalServiceRepository.findById(service.getId())).thenReturn(Mono.just(service));
        when(clientRepository.findByServiceAndExternalUserId(service.getId().getValue(), EXTERNAL_USER_ID))
                .thenReturn(Mono.just(stale))
                .thenReturn(Mono.just(refreshed));
        when(clientJwtTokenService.generateAccessToken(any())).thenReturn(Mono.just(NEW_ACCESS_TOKEN));
        when(clientJwtTokenService.generateRefreshToken(any())).thenReturn(Mono.just(NEW_REFRESH_TOKEN));
        when(clientRepository.save(any(Client.class)))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("version conflict")));
        when(clientJwtTokenService.isTokenExpired(EXISTING_ACCESS_TOKEN)).thenReturn(Mono.just(false));
        when(externalServiceRepository.save(any(ExternalService.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(service.getId().getValue(), byExternalUser()))
                .assertNext(response -> assertEquals(EXISTING_ACCESS_TOKEN, response.accessToken()))
                .verifyComplete();

        verify(clientRepository, times(1)).save(any(Client.class));
    }

    @Test
    void exhaustedRetriesWithoutUsableTokenSurfaceOptimisticLockException() {
        Client stale = clientWithTokens(null, null);

        when(externalServiceRepository.findById(service.getId())).thenReturn(Mono.just(service));
        when(clientRepository.findByServiceAndExternalUserId(service.getId().getValue(), EXTERNAL_USER_ID))
                .thenReturn(Mono.just(stale));
        when(clientJwtTokenService.generateAccessToken(any())).thenReturn(Mono.just(NEW_ACCESS_TOKEN));
        when(clientJwtTokenService.generateRefreshToken(any())).thenReturn(Mono.just(NEW_REFRESH_TOKEN));
        when(clientRepository.save(any(Client.class)))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("persistent version conflict")));

        StepVerifier.create(useCase.execute(service.getId().getValue(), byExternalUser()))
                .expectErrorSatisfies(err ->
                        Assertions.assertInstanceOf(OptimisticLockingFailureException.class, err))
                .verify();
    }

    @Test
    void exhaustedRetriesReuseConcurrentlyWrittenTokenInsteadOfFailing() {
        Client noToken = clientWithTokens(null, null);
        Client concurrentlyWritten = clientWithTokens(EXISTING_ACCESS_TOKEN, EXISTING_REFRESH_TOKEN);
        java.util.concurrent.atomic.AtomicInteger reads = new java.util.concurrent.atomic.AtomicInteger();

        when(externalServiceRepository.findById(service.getId())).thenReturn(Mono.just(service));
        when(clientRepository.findByServiceAndExternalUserId(service.getId().getValue(), EXTERNAL_USER_ID))
                .thenAnswer(inv -> Mono.just(reads.incrementAndGet() >= 5 ? concurrentlyWritten : noToken));
        when(externalServiceRepository.save(any(ExternalService.class)))
                .thenReturn(Mono.just(service));
        when(clientJwtTokenService.generateAccessToken(any())).thenReturn(Mono.just(NEW_ACCESS_TOKEN));
        when(clientJwtTokenService.generateRefreshToken(any())).thenReturn(Mono.just(NEW_REFRESH_TOKEN));
        when(clientRepository.save(any(Client.class)))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("version conflict")));
        when(clientJwtTokenService.isTokenExpired(EXISTING_ACCESS_TOKEN)).thenReturn(Mono.just(false));

        StepVerifier.create(useCase.execute(service.getId().getValue(), byExternalUser()))
                .assertNext(response -> assertEquals(EXISTING_ACCESS_TOKEN, response.accessToken()))
                .verifyComplete();
    }

    @Test
    void expiresInReflectsConfiguredAccessTokenTtl() {
        when(clientJwtProperties.getAccessTokenExpiration()).thenReturn(Duration.ofDays(7));
        Client client = clientWithTokens(EXISTING_ACCESS_TOKEN, EXISTING_REFRESH_TOKEN);

        when(externalServiceRepository.findById(service.getId())).thenReturn(Mono.just(service));
        when(clientRepository.findByServiceAndExternalUserId(service.getId().getValue(), EXTERNAL_USER_ID))
                .thenReturn(Mono.just(client));
        when(clientJwtTokenService.isTokenExpired(EXISTING_ACCESS_TOKEN)).thenReturn(Mono.just(false));
        when(externalServiceRepository.save(any(ExternalService.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(service.getId().getValue(), byExternalUser()))
                .assertNext(response -> assertEquals(Duration.ofDays(7).toSeconds(), response.expiresIn()))
                .verifyComplete();
    }

    @Test
    void failsWithClientManagementNotAllowedWhenServiceCannotManageClients() {
        ExternalService restrictedService = ExternalService.create(
                "Restricted",
                "no client mgmt",
                AdminId.generate(),
                List.of("/api/v1/integration/**"),
                60,
                false
        );

        when(externalServiceRepository.findById(restrictedService.getId()))
                .thenReturn(Mono.just(restrictedService));

        StepVerifier.create(useCase.execute(restrictedService.getId().getValue(), byExternalUser()))
                .expectErrorSatisfies(err ->
                        Assertions.assertInstanceOf(ClientManagementNotAllowedException.class, err))
                .verify();

        verify(clientRepository, never()).save(any(Client.class));
        verify(clientRepository, never())
                .findByServiceAndExternalUserId(any(), any());
    }

    @Test
    void clientIdPathDoesNotAutoCreateWhenNotFound() {
        String missingClientId = ClientId.generate().getValue();
        IntegrationClientTokenRequest byClientId = new IntegrationClientTokenRequest(missingClientId, null, null);

        when(externalServiceRepository.findById(service.getId())).thenReturn(Mono.just(service));
        when(clientRepository.findById(ClientId.of(missingClientId)))
                .thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(service.getId().getValue(), byClientId))
                .expectErrorSatisfies(err -> Assertions.assertInstanceOf(
                        biz.ugur.busroutebackend.integration.domain.exceptions.IntegrationClientNotFoundException.class,
                        err))
                .verify();

        verify(clientRepository, never()).save(any(Client.class));
    }

    @Test
    void newClientStoresCanonicalRealPhoneWhenPhoneProvided() {
        String serviceId = service.getId().getValue();

        when(externalServiceRepository.findById(service.getId())).thenReturn(Mono.just(service));
        when(clientRepository.findByServiceAndExternalUserId(serviceId, EXTERNAL_USER_ID)).thenReturn(Mono.empty());
        when(clientRepository.findByPhone(CANON_PHONE)).thenReturn(Mono.empty());
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(clientJwtTokenService.generateAccessToken(any())).thenReturn(Mono.just(NEW_ACCESS_TOKEN));
        when(clientJwtTokenService.generateRefreshToken(any())).thenReturn(Mono.just(NEW_REFRESH_TOKEN));
        when(externalServiceRepository.save(any(ExternalService.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(serviceId, byExternalUserWithPhone(REAL_PHONE_8)))
                .assertNext(response -> assertEquals(EXTERNAL_USER_ID, response.externalUserId()))
                .verifyComplete();

        ArgumentCaptor<Client> saved = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        Client created = saved.getAllValues().get(0);
        assertEquals(CANON_PHONE, created.getPhoneNumber());
        assertEquals(EXTERNAL_USER_ID, created.getExternalUserId());
    }

    @Test
    void mergesWithExistingDirectClientWhenRealPhoneMatches() {
        String serviceId = service.getId().getValue();
        Client direct = directClient(CANON_PHONE, EXISTING_ACCESS_TOKEN, EXISTING_REFRESH_TOKEN);

        when(externalServiceRepository.findById(service.getId())).thenReturn(Mono.just(service));
        when(clientRepository.findByServiceAndExternalUserId(serviceId, EXTERNAL_USER_ID)).thenReturn(Mono.empty());
        when(clientRepository.findByPhone(CANON_PHONE)).thenReturn(Mono.just(direct));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(clientJwtTokenService.isTokenExpired(EXISTING_ACCESS_TOKEN)).thenReturn(Mono.just(false));
        when(externalServiceRepository.save(any(ExternalService.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(serviceId, byExternalUserWithPhone(REAL_PHONE_8)))
                .assertNext(response -> {
                    assertEquals(EXISTING_ACCESS_TOKEN, response.accessToken());
                    assertEquals(EXTERNAL_USER_ID, response.externalUserId());
                })
                .verifyComplete();

        ArgumentCaptor<Client> saved = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        Client merged = saved.getAllValues().get(0);
        assertEquals(direct.getId().getValue(), merged.getId().getValue());
        assertEquals(EXTERNAL_USER_ID, merged.getExternalUserId());
        assertEquals(serviceId, merged.getCreatedByServiceId());
        assertEquals(CANON_PHONE, merged.getPhoneNumber());
    }

    @Test
    void lazyUpgradesSyntheticPhoneWhenFoundByExternalUserIdAndPhoneFree() {
        String serviceId = service.getId().getValue();
        Client synthetic = syntheticIntegrationClient(EXISTING_ACCESS_TOKEN, EXISTING_REFRESH_TOKEN);

        when(externalServiceRepository.findById(service.getId())).thenReturn(Mono.just(service));
        when(clientRepository.findByServiceAndExternalUserId(serviceId, EXTERNAL_USER_ID)).thenReturn(Mono.just(synthetic));
        when(clientRepository.findByPhone(CANON_PHONE)).thenReturn(Mono.empty());
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(clientJwtTokenService.isTokenExpired(EXISTING_ACCESS_TOKEN)).thenReturn(Mono.just(false));
        when(externalServiceRepository.save(any(ExternalService.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(serviceId, byExternalUserWithPhone(REAL_PHONE_8)))
                .assertNext(response -> assertEquals(EXISTING_ACCESS_TOKEN, response.accessToken()))
                .verifyComplete();

        ArgumentCaptor<Client> saved = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertEquals(CANON_PHONE, saved.getValue().getPhoneNumber());
    }

    @Test
    void keepsSyntheticPhoneWhenLazyUpgradeTargetTaken() {
        String serviceId = service.getId().getValue();
        Client synthetic = syntheticIntegrationClient(EXISTING_ACCESS_TOKEN, EXISTING_REFRESH_TOKEN);
        Client otherOwner = directClient(CANON_PHONE, null, null);

        when(externalServiceRepository.findById(service.getId())).thenReturn(Mono.just(service));
        when(clientRepository.findByServiceAndExternalUserId(serviceId, EXTERNAL_USER_ID)).thenReturn(Mono.just(synthetic));
        when(clientRepository.findByPhone(CANON_PHONE)).thenReturn(Mono.just(otherOwner));
        when(clientJwtTokenService.isTokenExpired(EXISTING_ACCESS_TOKEN)).thenReturn(Mono.just(false));
        when(externalServiceRepository.save(any(ExternalService.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(serviceId, byExternalUserWithPhone(REAL_PHONE_8)))
                .assertNext(response -> assertEquals(EXISTING_ACCESS_TOKEN, response.accessToken()))
                .verifyComplete();

        verify(clientRepository, never()).save(any(Client.class));
    }

    @Test
    void lazyUpgradeRaceOnDuplicateKeyKeepsSyntheticInsteadOfFailing() {
        String serviceId = service.getId().getValue();
        Client synthetic = syntheticIntegrationClient(EXISTING_ACCESS_TOKEN, EXISTING_REFRESH_TOKEN);

        when(externalServiceRepository.findById(service.getId())).thenReturn(Mono.just(service));
        when(clientRepository.findByServiceAndExternalUserId(serviceId, EXTERNAL_USER_ID)).thenReturn(Mono.just(synthetic));
        when(clientRepository.findByPhone(CANON_PHONE)).thenReturn(Mono.empty());
        when(clientRepository.save(any(Client.class)))
                .thenReturn(Mono.error(new DuplicateKeyException("race on phone")));
        when(clientJwtTokenService.isTokenExpired(EXISTING_ACCESS_TOKEN)).thenReturn(Mono.just(false));
        when(externalServiceRepository.save(any(ExternalService.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(serviceId, byExternalUserWithPhone(REAL_PHONE_8)))
                .assertNext(response -> assertEquals(EXISTING_ACCESS_TOKEN, response.accessToken()))
                .verifyComplete();
    }

    @Test
    void phoneCollisionDuplicateKeyMergesInsteadOfFailing() {
        String serviceId = service.getId().getValue();
        Client raceWinner = directClient(CANON_PHONE, EXISTING_ACCESS_TOKEN, EXISTING_REFRESH_TOKEN);

        when(externalServiceRepository.findById(service.getId())).thenReturn(Mono.just(service));
        when(clientRepository.findByServiceAndExternalUserId(serviceId, EXTERNAL_USER_ID))
                .thenReturn(Mono.empty())
                .thenReturn(Mono.empty());
        when(clientRepository.findByPhone(CANON_PHONE))
                .thenReturn(Mono.empty())
                .thenReturn(Mono.just(raceWinner));
        when(clientRepository.save(any(Client.class)))
                .thenReturn(Mono.error(new DuplicateKeyException("duplicate phone")))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(clientJwtTokenService.isTokenExpired(EXISTING_ACCESS_TOKEN)).thenReturn(Mono.just(false));
        when(externalServiceRepository.save(any(ExternalService.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(serviceId, byExternalUserWithPhone(REAL_PHONE_8)))
                .assertNext(response -> assertEquals(EXISTING_ACCESS_TOKEN, response.accessToken()))
                .verifyComplete();
    }
}
