package biz.ugur.busroutebackend.integration.application.usecase;

import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.integration.application.dto.UpdateExternalServiceRequest;
import biz.ugur.busroutebackend.integration.domain.exceptions.ExternalServiceNotFoundException;
import biz.ugur.busroutebackend.integration.domain.model.ExternalService;
import biz.ugur.busroutebackend.integration.domain.repository.ExternalServiceRepository;
import biz.ugur.busroutebackend.integration.domain.valueobjects.ExternalServiceId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class UpdateExternalServiceUseCaseTest {

    @InjectMocks
    private UpdateExternalServiceUseCase useCase;

    @Mock
    private ExternalServiceRepository externalServiceRepository;

    private ExternalService service;

    @BeforeEach
    void setUp() {
        service = ExternalService.create(
                "Partner",
                "old description",
                AdminId.generate(),
                List.of("/api/v1/**"),
                60,
                false
        );
    }

    @Test
    void updatesFieldsWhenNameUnchanged() {
        UpdateExternalServiceRequest req = new UpdateExternalServiceRequest(
                "Partner", "new description", List.of("/api/v2/**"), 120);

        when(externalServiceRepository.findById(service.getId())).thenReturn(Mono.just(service));
        when(externalServiceRepository.save(any(ExternalService.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(service.getId().getValue(), req))
                .assertNext(dto -> {
                    assertEquals("Partner", dto.name());
                    assertEquals("new description", dto.description());
                    assertEquals(120, dto.rateLimitPerMinute());
                })
                .verifyComplete();
    }

    @Test
    void errorsWhenRenameCollides() {
        UpdateExternalServiceRequest req = new UpdateExternalServiceRequest(
                "NewName", null, null, 60);

        when(externalServiceRepository.findById(service.getId())).thenReturn(Mono.just(service));
        when(externalServiceRepository.existsByName("NewName")).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute(service.getId().getValue(), req))
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalArgumentException.class, err))
                .verify();
    }

    @Test
    void renamesWhenNewNameNotTaken() {
        UpdateExternalServiceRequest req = new UpdateExternalServiceRequest(
                "NewName", null, null, 60);

        when(externalServiceRepository.findById(service.getId())).thenReturn(Mono.just(service));
        when(externalServiceRepository.existsByName("NewName")).thenReturn(Mono.just(false));
        when(externalServiceRepository.save(any(ExternalService.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(service.getId().getValue(), req))
                .assertNext(dto -> assertEquals("NewName", dto.name()))
                .verifyComplete();
    }

    @Test
    void errorsWhenServiceNotFound() {
        UpdateExternalServiceRequest req = new UpdateExternalServiceRequest(
                "X", null, null, 60);
        String id = ExternalServiceId.generate().getValue();

        when(externalServiceRepository.findById(any(ExternalServiceId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(id, req))
                .expectErrorSatisfies(err -> assertInstanceOf(ExternalServiceNotFoundException.class, err))
                .verify();
    }
}
