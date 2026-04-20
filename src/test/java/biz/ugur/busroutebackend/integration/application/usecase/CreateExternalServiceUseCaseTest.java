package biz.ugur.busroutebackend.integration.application.usecase;

import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.integration.application.dto.CreateExternalServiceRequest;
import biz.ugur.busroutebackend.integration.domain.model.ExternalService;
import biz.ugur.busroutebackend.integration.domain.repository.ExternalServiceRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class CreateExternalServiceUseCaseTest {

    @InjectMocks
    private CreateExternalServiceUseCase useCase;

    @Mock
    private ExternalServiceRepository externalServiceRepository;

    @Test
    void createsServiceAndReturnsDTOWithToken() {
        CreateExternalServiceRequest request = new CreateExternalServiceRequest(
                "Partner", "description", List.of("/api/v1/**"), 120, true);
        AdminId admin = AdminId.generate();

        when(externalServiceRepository.existsByName("Partner")).thenReturn(Mono.just(false));
        when(externalServiceRepository.save(any(ExternalService.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(useCase.execute(request, admin))
                .assertNext(dto -> {
                    assertEquals("Partner", dto.name());
                    assertEquals("description", dto.description());
                    assertNotNull(dto.apiToken());
                    assertNotNull(dto.maskedToken());
                })
                .verifyComplete();

        verify(externalServiceRepository).save(any(ExternalService.class));
    }

    @Test
    void errorsWhenNameAlreadyExists() {
        CreateExternalServiceRequest request = new CreateExternalServiceRequest(
                "Existing", null, null, 60, false);
        AdminId admin = AdminId.generate();

        when(externalServiceRepository.existsByName("Existing")).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute(request, admin))
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalArgumentException.class, err))
                .verify();
    }
}
