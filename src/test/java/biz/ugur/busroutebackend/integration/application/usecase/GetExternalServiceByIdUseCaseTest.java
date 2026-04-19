package biz.ugur.busroutebackend.integration.application.usecase;

import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetExternalServiceByIdUseCaseTest {

    @InjectMocks
    private GetExternalServiceByIdUseCase useCase;

    @Mock
    private ExternalServiceRepository externalServiceRepository;

    private ExternalService service;

    @BeforeEach
    void setUp() {
        service = ExternalService.create(
                "Partner",
                "description",
                AdminId.generate(),
                List.of("/api/v1/**"),
                60,
                true
        );
    }

    @Test
    void returnsMaskedDTOWhenFound() {
        when(externalServiceRepository.findById(service.getId())).thenReturn(Mono.just(service));

        StepVerifier.create(useCase.execute(service.getId().getValue()))
                .assertNext(dto -> {
                    assertEquals("Partner", dto.name());
                    assertNull(dto.apiToken());
                    assertEquals(service.getApiToken().getMaskedValue(), dto.maskedToken());
                })
                .verifyComplete();
    }

    @Test
    void errorsWhenServiceNotFound() {
        String id = ExternalServiceId.generate().getValue();
        when(externalServiceRepository.findById(any(ExternalServiceId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(id))
                .expectErrorSatisfies(err -> assertInstanceOf(ExternalServiceNotFoundException.class, err))
                .verify();
    }
}
