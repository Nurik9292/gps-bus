package biz.ugur.busroutebackend.integration.application.usecase;

import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.client.domain.enums.ClientStatus;
import biz.ugur.busroutebackend.client.domain.enums.Platform;
import biz.ugur.busroutebackend.client.domain.model.Client;
import biz.ugur.busroutebackend.client.domain.repository.ClientRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.integration.application.dto.RegisterIntegrationClientRequest;
import biz.ugur.busroutebackend.integration.domain.exceptions.IntegrationClientAlreadyExistsException;
import biz.ugur.busroutebackend.integration.domain.model.ExternalService;
import biz.ugur.busroutebackend.integration.domain.repository.ExternalServiceRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class RegisterIntegrationClientUseCaseTest {

    private static final String EXTERNAL_USER_ID = "1980";
    private static final String NAME = "Merdan";
    private static final String REAL_PHONE_8 = "61520000";
    private static final String CANON_PHONE = "99361520000";

    @InjectMocks
    private RegisterIntegrationClientUseCase useCase;

    @Mock
    private ExternalServiceRepository externalServiceRepository;

    @Mock
    private ClientRepository clientRepository;

    private ExternalService service;
    private String serviceId;

    @BeforeEach
    void setUp() {
        service = ExternalService.create(
                "PartnerSvc",
                "test partner",
                AdminId.generate(),
                List.of("/api/v1/integration/**"),
                60,
                true
        );
        serviceId = service.getId().getValue();
        when(externalServiceRepository.findById(service.getId())).thenReturn(Mono.just(service));
    }

    private RegisterIntegrationClientRequest request(String phone) {
        return new RegisterIntegrationClientRequest(NAME, EXTERNAL_USER_ID, phone);
    }

    private Client directClient(String phone) {
        return Client.fromDatabase(
                ClientId.generate(),
                "Прямой",
                phone,
                null,
                true,
                Platform.ANDROID,
                ClientStatus.ACTIVE,
                LocalDateTime.now(),
                null,
                null,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now().minusDays(1),
                1L,
                null,
                null
        );
    }

    @Test
    void createsClientWithCanonicalRealPhoneWhenPhoneProvided() {
        when(clientRepository.findByServiceAndExternalUserId(serviceId, EXTERNAL_USER_ID)).thenReturn(Mono.empty());
        when(clientRepository.findByPhone(CANON_PHONE)).thenReturn(Mono.empty());
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(serviceId, request(REAL_PHONE_8)))
                .assertNext(dto -> assertEquals(EXTERNAL_USER_ID, dto.externalUserId()))
                .verifyComplete();

        ArgumentCaptor<Client> saved = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(saved.capture());
        assertEquals(CANON_PHONE, saved.getValue().getPhoneNumber());
        assertEquals(EXTERNAL_USER_ID, saved.getValue().getExternalUserId());
    }

    @Test
    void createsSyntheticClientWhenPhoneNull() {
        when(clientRepository.findByServiceAndExternalUserId(serviceId, EXTERNAL_USER_ID)).thenReturn(Mono.empty());
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(serviceId, request(null)))
                .assertNext(dto -> assertEquals(EXTERNAL_USER_ID, dto.externalUserId()))
                .verifyComplete();

        ArgumentCaptor<Client> saved = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(saved.capture());
        assertTrue(saved.getValue().getPhoneNumber().startsWith("+993INT"));
    }

    @Test
    void mergesWithExistingDirectClientWhenPhoneMatches() {
        Client direct = directClient(CANON_PHONE);
        when(clientRepository.findByServiceAndExternalUserId(serviceId, EXTERNAL_USER_ID)).thenReturn(Mono.empty());
        when(clientRepository.findByPhone(CANON_PHONE)).thenReturn(Mono.just(direct));
        when(clientRepository.save(any(Client.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(serviceId, request(REAL_PHONE_8)))
                .assertNext(dto -> {
                    assertEquals(direct.getId().getValue(), dto.clientId());
                    assertEquals(EXTERNAL_USER_ID, dto.externalUserId());
                })
                .verifyComplete();

        ArgumentCaptor<Client> saved = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(saved.capture());
        Client merged = saved.getValue();
        assertEquals(direct.getId().getValue(), merged.getId().getValue());
        assertEquals(serviceId, merged.getCreatedByServiceId());
        assertEquals(EXTERNAL_USER_ID, merged.getExternalUserId());
        assertEquals(CANON_PHONE, merged.getPhoneNumber());
    }

    @Test
    void rejectsWhenAlreadyRegisteredByExternalUserId() {
        Client existing = Client.createViaExternalService(NAME, serviceId, EXTERNAL_USER_ID, REAL_PHONE_8);
        when(clientRepository.findByServiceAndExternalUserId(serviceId, EXTERNAL_USER_ID)).thenReturn(Mono.just(existing));

        StepVerifier.create(useCase.execute(serviceId, request(REAL_PHONE_8)))
                .expectErrorSatisfies(err ->
                        Assertions.assertInstanceOf(IntegrationClientAlreadyExistsException.class, err))
                .verify();

        verify(clientRepository, never()).save(any(Client.class));
    }
}
